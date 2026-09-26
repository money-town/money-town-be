package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendDisbursementServiceTest {

    @Mock
    private DividendPayoutWriter payoutWriter;
    @Mock
    private SettlementFailureNotifier settlementFailureNotifier;

    @InjectMocks
    private DividendDisbursementService dividendDisbursementService;

    // ---------- disburse: claim(+Outbox 저장)까지만 한다. 지급은 지갑이 Kafka로 처리 ----------

    @Test
    @DisplayName("disburse: markDisbursing → claim만 하고 회차 마감 판정은 하지 않는다(지갑 결과 컨슈머가 처리)")
    void disburseOnlyClaimsAndDoesNotFinalize() {
        UUID batchId = UUID.randomUUID();
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.markDisbursing(batchId)).thenReturn(true);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));

        dividendDisbursementService.disburse(batchId);

        InOrder order = inOrder(payoutWriter);
        order.verify(payoutWriter).markDisbursing(batchId);
        order.verify(payoutWriter).claimPendingPayouts(batchId);
        verify(payoutWriter, never()).hasInProgressPayouts(any());
        verify(payoutWriter, never()).finalizeBatchIfDisbursing(any());
    }

    @Test
    @DisplayName("disburse: claim된 건이 없으면 메시지가 없으므로 직접 마감 판정을 한다")
    void disburseFinalizesDirectlyWhenNothingClaimed() {
        UUID batchId = UUID.randomUUID();
        SettlementBatch completedBatch = batchWithStatus(batchId, SettlementStatus.COMPLETED);
        when(payoutWriter.markDisbursing(batchId)).thenReturn(true);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of());
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.of(completedBatch));

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter).finalizeBatchIfDisbursing(batchId);
        verify(settlementFailureNotifier, never()).notifyDividendBatchFailed(any());
    }

    @Test
    @DisplayName("disburse: 이미 종결된 회차(markDisbursing=false)면 claim·마감 판정 없이 중단한다")
    void disburseSkipsWhenBatchAlreadyClosed() {
        UUID batchId = UUID.randomUUID();
        when(payoutWriter.markDisbursing(batchId)).thenReturn(false);

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter, never()).claimPendingPayouts(any());
        verify(payoutWriter, never()).hasInProgressPayouts(any());
        verify(payoutWriter, never()).finalizeBatchIfDisbursing(any());
    }

    @Test
    @DisplayName("존재하지 않는 정산 회차면 markDisbursing에서 던진 예외가 그대로 전파되고, claim은 일어나지 않는다")
    void propagatesExceptionWhenBatchNotFound() {
        UUID batchId = UUID.randomUUID();
        RuntimeException notFound = new RuntimeException("batch not found");
        doThrow(notFound).when(payoutWriter).markDisbursing(batchId);

        assertThatThrownBy(() -> dividendDisbursementService.disburse(batchId))
                .isSameAs(notFound);

        verify(payoutWriter, never()).claimPendingPayouts(any());
    }

    // ---------- applyDispatchResult: 지갑 결과 컨슈머가 payout 1건씩 결과를 반영 ----------

    @Test
    @DisplayName("PROCESSING인 건에 성공 결과가 오면: markPaid → 진행 중 건 확인 순서로 처리하고, 남은 건이 있으면 마감 판정을 하지 않는다")
    void appliesSucceededResultInOrder() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.applyDispatchResult(payoutId, batchId, true, null);

        InOrder order = inOrder(payoutWriter);
        order.verify(payoutWriter).isProcessing(payoutId);
        order.verify(payoutWriter).markPaid(payoutId);
        order.verify(payoutWriter).hasInProgressPayouts(batchId);
        verify(payoutWriter, never()).markFailedAttempt(any());
        verify(payoutWriter, never()).finalizeBatchIfDisbursing(any());
    }

    @Test
    @DisplayName("이미 PROCESSING이 아닌 건(중복 수신·회수됨)은 결과를 반영하지 않는다")
    void skipsPayoutThatIsNotProcessing() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(false);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.applyDispatchResult(payoutId, batchId, true, null);

        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter, never()).markFailedAttempt(any());
    }

    @Test
    @DisplayName("PROCESSING인 건에 실패 결과가 오면 markPaid 대신 markFailedAttempt를 호출한다")
    void appliesFailedResult() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.applyDispatchResult(payoutId, batchId, false, "WALLET_NOT_FOUND");

        verify(payoutWriter).markFailedAttempt(payoutId);
        verify(payoutWriter, never()).markPaid(any());
    }

    // ---------- 회차 마감 판정 ----------

    @Test
    @DisplayName("마지막 건의 실패 결과를 반영해 진행 중인 건이 없어지면 마감 판정을 하고, FAILED면 정산 실패 알림을 보낸다")
    void finalizesAndNotifiesFailureWhenLastPayoutDone() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        SettlementBatch failedBatch = batchWithStatus(batchId, SettlementStatus.FAILED);
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.of(failedBatch));

        dividendDisbursementService.applyDispatchResult(payoutId, batchId, false, "WALLET_NOT_FOUND");

        verify(settlementFailureNotifier).notifyDividendBatchFailed(failedBatch);
    }

    @Test
    @DisplayName("PARTIAL_FAILED로 확정돼도 정산 실패 알림을 보낸다")
    void notifiesFailureWhenBatchEndsPartialFailed() {
        UUID batchId = UUID.randomUUID();
        SettlementBatch partialFailedBatch = batchWithStatus(batchId, SettlementStatus.PARTIAL_FAILED);
        when(payoutWriter.isProcessing(any())).thenReturn(false);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.of(partialFailedBatch));

        dividendDisbursementService.applyDispatchResult(UUID.randomUUID(), batchId, true, null);

        verify(settlementFailureNotifier).notifyDividendBatchFailed(partialFailedBatch);
    }

    @Test
    @DisplayName("COMPLETED로 확정되면 실패 알림을 보내지 않는다")
    void doesNotNotifyFailureWhenBatchCompleted() {
        UUID batchId = UUID.randomUUID();
        SettlementBatch completedBatch = batchWithStatus(batchId, SettlementStatus.COMPLETED);
        when(payoutWriter.isProcessing(any())).thenReturn(false);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.of(completedBatch));

        dividendDisbursementService.applyDispatchResult(UUID.randomUUID(), batchId, true, null);

        verify(settlementFailureNotifier, never()).notifyDividendBatchFailed(any());
    }

    @Test
    @DisplayName("다른 컨슈머가 이미 마감했으면(finalizeBatchIfDisbursing이 빈 값) 알림을 다시 보내지 않는다")
    void doesNotNotifyTwiceWhenAnotherConsumerAlreadyFinalized() {
        UUID batchId = UUID.randomUUID();
        when(payoutWriter.isProcessing(any())).thenReturn(false);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.empty());

        dividendDisbursementService.applyDispatchResult(UUID.randomUUID(), batchId, true, null);

        verify(settlementFailureNotifier, never()).notifyDividendBatchFailed(any());
    }

    @Test
    @DisplayName("reclaimStalledProcessing: payoutWriter로 위임하고 결과를 그대로 반환한다")
    void reclaimStalledProcessingDelegatesToPayoutWriter() {
        Instant staleBefore = Instant.now();
        when(payoutWriter.reclaimStalledProcessing(staleBefore)).thenReturn(2);

        int reclaimed = dividendDisbursementService.reclaimStalledProcessing(staleBefore);

        assertThat(reclaimed).isEqualTo(2);
        verify(payoutWriter).reclaimStalledProcessing(staleBefore);
    }

    private SettlementBatch batchWithStatus(UUID batchId, SettlementStatus status) {
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000_000L);
        ReflectionTestUtils.setField(batch, "id", batchId);
        ReflectionTestUtils.setField(batch, "status", status);
        return batch;
    }
}