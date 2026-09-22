package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendDisbursementServiceTest {

    @Mock
    private DividendPayoutWriter payoutWriter;
    @Mock
    private WalletServiceClient walletServiceClient;
    @Mock
    private SettlementFailureNotifier settlementFailureNotifier;

    @InjectMocks
    private DividendDisbursementService dividendDisbursementService;

    // ---------- disburse: claim(+Outbox 저장)까지만 한다. 지갑 호출은 컨슈머 몫 ----------

    @Test
    @DisplayName("disburse: markDisbursing → claim만 하고 지갑 호출·회차 마감 판정은 하지 않는다(컨슈머가 payout별로 처리)")
    void disburseOnlyClaimsAndDoesNotCallWallet() {
        UUID batchId = UUID.randomUUID();
        DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of(payout));

        dividendDisbursementService.disburse(batchId);

        InOrder order = inOrder(payoutWriter);
        order.verify(payoutWriter).markDisbursing(batchId);
        order.verify(payoutWriter).claimPendingPayouts(batchId);
        verifyNoInteractions(walletServiceClient);
        verify(payoutWriter, never()).hasInProgressPayouts(any());
        verify(payoutWriter, never()).finalizeBatchIfDisbursing(any());
    }

    @Test
    @DisplayName("disburse: claim된 건이 없으면 메시지가 없으므로 직접 마감 판정을 한다")
    void disburseFinalizesDirectlyWhenNothingClaimed() {
        UUID batchId = UUID.randomUUID();
        SettlementBatch completedBatch = batchWithStatus(batchId, SettlementStatus.COMPLETED);
        when(payoutWriter.claimPendingPayouts(batchId)).thenReturn(List.of());
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.of(completedBatch));

        dividendDisbursementService.disburse(batchId);

        verify(payoutWriter).finalizeBatchIfDisbursing(batchId);
        verify(settlementFailureNotifier, never()).notifyDividendBatchFailed(any());
    }

    @Test
    @DisplayName("존재하지 않는 정산 회차면 markDisbursing에서 던진 예외가 그대로 전파되고, claim·지갑 호출은 일어나지 않는다")
    void propagatesExceptionWhenBatchNotFound() {
        UUID batchId = UUID.randomUUID();
        RuntimeException notFound = new RuntimeException("batch not found");
        doThrow(notFound).when(payoutWriter).markDisbursing(batchId);

        assertThatThrownBy(() -> dividendDisbursementService.disburse(batchId))
                .isSameAs(notFound);

        verify(walletServiceClient, never()).depositDividend(any());
        verify(payoutWriter, never()).claimPendingPayouts(any());
    }

    // ---------- processDispatchedPayout: 컨슈머가 payout 1건씩 처리 ----------

    @Test
    @DisplayName("PROCESSING인 건: 지갑 호출 → markPaid → 진행 중 건 확인 순서로 처리하고, 남은 건이 있으면 마감 판정을 하지 않는다")
    void processesDispatchedPayoutInOrder() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        UUID investorId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        DividendDepositResponse response = new DividendDepositResponse(9012L, 55L, "DIVIDEND", 1_000_000L, batchId, Instant.now());
        when(walletServiceClient.depositDividend(any())).thenReturn(ApiResponse.success(response, null));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, investorId, 1_000_000L);

        InOrder order = inOrder(payoutWriter, walletServiceClient);
        order.verify(payoutWriter).isProcessing(payoutId);
        order.verify(walletServiceClient).depositDividend(any());
        order.verify(payoutWriter).markPaid(payoutId);
        order.verify(payoutWriter).hasInProgressPayouts(batchId);
        verify(payoutWriter, never()).markFailedAttempt(any());
        verify(payoutWriter, never()).finalizeBatchIfDisbursing(any());

        ArgumentCaptor<DividendDepositRequest> requestCaptor = ArgumentCaptor.forClass(DividendDepositRequest.class);
        verify(walletServiceClient).depositDividend(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo(payoutId.toString());
        assertThat(requestCaptor.getValue().investorId()).isEqualTo(investorId);
        assertThat(requestCaptor.getValue().settlementBatchId()).isEqualTo(batchId);
        assertThat(requestCaptor.getValue().amount()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("이미 PROCESSING이 아닌 건(중복 수신·회수됨)은 지갑을 다시 부르지 않는다")
    void skipsPayoutThatIsNotProcessing() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(false);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

        verifyNoInteractions(walletServiceClient);
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter, never()).markFailedAttempt(any());
    }

    @Test
    @DisplayName("지갑 응답이 success=false면 예외가 없어도 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptWhenResponseSuccessIsFalse() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(walletServiceClient.depositDividend(any()))
                .thenReturn(new ApiResponse<>(false, null, "지갑 처리 실패", "WALLET_500_01"));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

        verify(payoutWriter).markFailedAttempt(payoutId);
        verify(payoutWriter, never()).markPaid(any());
    }

    @Test
    @DisplayName("지갑 호출이 FeignException을 던지면 markPaid 대신 markFailedAttempt를 호출한다")
    void marksFailedAttemptOnFeignException() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(walletServiceClient.depositDividend(any())).thenThrow(mock(FeignException.class));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

        verify(payoutWriter).markFailedAttempt(payoutId);
        verify(payoutWriter, never()).markPaid(any());
    }

    @Test
    @DisplayName("FeignException이 아닌 예외가 나도 claim된 건이 PROCESSING에 갇히지 않도록 markFailedAttempt를 호출한다")
    void marksFailedAttemptOnUnexpectedException() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(walletServiceClient.depositDividend(any())).thenThrow(new RuntimeException("unexpected"));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

        verify(payoutWriter).markFailedAttempt(payoutId);
        verify(payoutWriter, never()).markPaid(any());
    }

    @Test
    @DisplayName("지갑 응답의 settlementBatchId가 요청과 다르면 markPaid 대신 즉시 markResponseMismatch를 호출한다")
    void marksResponseMismatchWhenSettlementBatchIdDiffers() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        DividendDepositResponse response = new DividendDepositResponse(9012L, 55L, "DIVIDEND", 1_000_000L, UUID.randomUUID(), Instant.now());
        when(walletServiceClient.depositDividend(any())).thenReturn(ApiResponse.success(response, null));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

        verify(payoutWriter).markResponseMismatch(payoutId);
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter, never()).markFailedAttempt(any());
    }

    @Test
    @DisplayName("지갑 응답이 success=true인데 data가 없으면 불일치로 간주해 즉시 markResponseMismatch를 호출한다")
    void marksResponseMismatchWhenDataIsNull() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(walletServiceClient.depositDividend(any())).thenReturn(ApiResponse.success(null, null));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(true);

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

        verify(payoutWriter).markResponseMismatch(payoutId);
        verify(payoutWriter, never()).markPaid(any());
        verify(payoutWriter, never()).markFailedAttempt(any());
    }

    // ---------- 회차 마감 판정 ----------

    @Test
    @DisplayName("마지막 건을 처리해 진행 중인 건이 없어지면 마감 판정을 하고, FAILED면 정산 실패 알림을 보낸다")
    void finalizesAndNotifiesFailureWhenLastPayoutDone() {
        UUID batchId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        SettlementBatch failedBatch = batchWithStatus(batchId, SettlementStatus.FAILED);
        when(payoutWriter.isProcessing(payoutId)).thenReturn(true);
        when(walletServiceClient.depositDividend(any())).thenThrow(new RuntimeException("wallet down"));
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.of(failedBatch));

        dividendDisbursementService.processDispatchedPayout(payoutId, batchId, UUID.randomUUID(), 1_000_000L);

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

        dividendDisbursementService.processDispatchedPayout(UUID.randomUUID(), batchId, UUID.randomUUID(), 1_000_000L);

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

        dividendDisbursementService.processDispatchedPayout(UUID.randomUUID(), batchId, UUID.randomUUID(), 1_000_000L);

        verify(settlementFailureNotifier, never()).notifyDividendBatchFailed(any());
    }

    @Test
    @DisplayName("다른 컨슈머가 이미 마감했으면(finalizeBatchIfDisbursing이 빈 값) 알림을 다시 보내지 않는다")
    void doesNotNotifyTwiceWhenAnotherConsumerAlreadyFinalized() {
        UUID batchId = UUID.randomUUID();
        when(payoutWriter.isProcessing(any())).thenReturn(false);
        when(payoutWriter.hasInProgressPayouts(batchId)).thenReturn(false);
        when(payoutWriter.finalizeBatchIfDisbursing(batchId)).thenReturn(Optional.empty());

        dividendDisbursementService.processDispatchedPayout(UUID.randomUUID(), batchId, UUID.randomUUID(), 1_000_000L);

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