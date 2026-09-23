package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.DividendPayoutDispatchPayload;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.ResolutionType;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendPayoutWriterTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;
    @Mock
    private OutboxEventStore outboxEventStore;
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private DividendPayoutWriter dividendPayoutWriter;

    @Test
    @DisplayName("markDisbursing: 배치를 DISBURSING으로 전환하고 저장한다")
    void marksDisbursing() {
        SettlementBatch batch = openBatch();
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));

        dividendPayoutWriter.markDisbursing(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.DISBURSING);
        verify(settlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("markDisbursing: 존재하지 않는 배치면 예외")
    void marksDisbursing_batchNotFound() {
        UUID unknownBatchId = UUID.randomUUID();
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(unknownBatchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dividendPayoutWriter.markDisbursing(unknownBatchId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND);
    }

    @Test
    @DisplayName("claimPendingPayouts: QUEUED/RETRYING 건을 PROCESSING으로 전환해 선점하고 반환한다")
    void claimsPendingPayoutsAndMarksProcessing() {
        UUID batchId = UUID.randomUUID();
        DividendPayout queued = queuedPayout();
        DividendPayout retrying = retryingPayout(1);
        when(dividendPayoutRepository.findBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING)))
                .thenReturn(List.of(queued, retrying));

        List<DividendPayout> claimed = dividendPayoutWriter.claimPendingPayouts(batchId);

        assertThat(claimed).containsExactly(queued, retrying);
        assertThat(queued.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(retrying.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
        verify(dividendPayoutRepository).saveAll(claimed);
    }

    @Test
    @DisplayName("claimPendingPayouts: claim한 payout마다 Outbox에 지급 요청을 저장한다 (key=payoutId, topic=dividend-payout-dispatch-requested)")
    void claimStoresOutboxEventPerClaimedPayout() {
        UUID batchId = UUID.randomUUID();
        DividendPayout first = queuedPayout();
        DividendPayout second = queuedPayout();
        when(dividendPayoutRepository.findBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING)))
                .thenReturn(List.of(first, second));

        dividendPayoutWriter.claimPendingPayouts(batchId);

        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventStore, times(2)).save(
                eq(DividendPayoutDispatchPayload.AGGREGATE_TYPE), eq(DividendPayoutDispatchPayload.TOPIC), envelopeCaptor.capture());
        EventEnvelope<?> firstEnvelope = envelopeCaptor.getAllValues().get(0);
        assertThat(firstEnvelope.eventType()).isEqualTo(DividendPayoutDispatchPayload.EVENT_TYPE);
        assertThat(firstEnvelope.aggregateId()).isEqualTo(first.getId().toString());
        assertThat(firstEnvelope.payload()).isEqualTo(new DividendPayoutDispatchPayload(
                first.getId(), batchId, first.getInvestorId(), first.getAmount()));
    }

    @Test
    @DisplayName("claimPendingPayouts: claim할 건이 없으면 Outbox에 아무것도 저장하지 않는다")
    void claimStoresNothingWhenNoPayouts() {
        UUID batchId = UUID.randomUUID();
        when(dividendPayoutRepository.findBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING)))
                .thenReturn(List.of());

        dividendPayoutWriter.claimPendingPayouts(batchId);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("isProcessing: PROCESSING인 건만 true, 다른 상태나 없는 건은 false")
    void isProcessingOnlyForProcessingPayouts() {
        DividendPayout processing = queuedPayout();
        processing.markProcessing();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        UUID unknown = UUID.randomUUID();
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(processing.getId())).thenReturn(Optional.of(processing));
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(paid.getId())).thenReturn(Optional.of(paid));
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(unknown)).thenReturn(Optional.empty());

        assertThat(dividendPayoutWriter.isProcessing(processing.getId())).isTrue();
        assertThat(dividendPayoutWriter.isProcessing(paid.getId())).isFalse();
        assertThat(dividendPayoutWriter.isProcessing(unknown)).isFalse();
    }

    @Test
    @DisplayName("hasInProgressPayouts: QUEUED/RETRYING/PROCESSING 존재 여부를 그대로 위임한다")
    void hasInProgressPayoutsDelegatesToExistsQuery() {
        UUID batchId = UUID.randomUUID();
        when(dividendPayoutRepository.existsBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING, PayoutStatus.PROCESSING)))
                .thenReturn(true);

        assertThat(dividendPayoutWriter.hasInProgressPayouts(batchId)).isTrue();
    }

    @Test
    @DisplayName("finalizeBatchIfDisbursing: DISBURSING이 아니면(이미 다른 컨슈머가 마감) 아무것도 하지 않고 빈 값을 돌려준다")
    void finalizeBatchIfDisbursing_skipsWhenAlreadyFinalized() {
        SettlementBatch batch = openBatch();
        ReflectionTestUtils.setField(batch, "status", SettlementStatus.COMPLETED);
        when(settlementBatchRepository.findWithLockByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));

        Optional<SettlementBatch> result = dividendPayoutWriter.finalizeBatchIfDisbursing(batch.getId());

        assertThat(result).isEmpty();
        verify(dividendPayoutRepository, never()).findBySettlementBatchIdAndIsDeletedFalse(any());
        verify(settlementBatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("finalizeBatchIfDisbursing: DISBURSING이면 전부 성공 시 COMPLETED로 마감한다")
    void finalizeBatchIfDisbursing_completesWhenAllPaid() {
        SettlementBatch batch = openBatch();
        batch.markDisbursing();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        when(settlementBatchRepository.findWithLockByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId())).thenReturn(List.of(paid));

        Optional<SettlementBatch> result = dividendPayoutWriter.finalizeBatchIfDisbursing(batch.getId());

        assertThat(result).containsSame(batch);
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    @Test
    @DisplayName("reclaimStalledProcessing: 기준 시각 이전에 멈춘 PROCESSING 건을 QUEUED로 되돌리되 retryCount는 보존한다")
    void reclaimsStalledProcessing() {
        DividendPayout stalled = retryingPayout(1);
        ReflectionTestUtils.setField(stalled, "status", PayoutStatus.PROCESSING);
        Instant staleBefore = Instant.now();
        when(dividendPayoutRepository.findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, staleBefore))
                .thenReturn(List.of(stalled));

        int reclaimed = dividendPayoutWriter.reclaimStalledProcessing(staleBefore);

        assertThat(reclaimed).isEqualTo(1);
        assertThat(stalled.getStatus()).isEqualTo(PayoutStatus.QUEUED);
        assertThat(stalled.getRetryCount()).isEqualTo(1);
        verify(dividendPayoutRepository).saveAll(List.of(stalled));
    }

    @Test
    @DisplayName("markPaid: 지급 건을 PAID로 전환하고 저장한다")
    void marksPaid() {
        DividendPayout payout = queuedPayout();
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        dividendPayoutWriter.markPaid(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        verify(dividendPayoutRepository).save(payout);
    }

    @Test
    @DisplayName("markFailedAttempt: 최대 재시도 미만이면 retryCount만 올리고 RETRYING으로 남긴다")
    void marksFailedAttempt_belowMaxRetry() {
        DividendPayout payout = queuedPayout();
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        dividendPayoutWriter.markFailedAttempt(payout.getId());

        assertThat(payout.getRetryCount()).isEqualTo(1);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RETRYING);
        verify(dividendPayoutRepository).save(payout);
    }

    @Test
    @DisplayName("markFailedAttempt: 3회째 실패하면 DEAD_LETTER로 전환한다")
    void marksFailedAttempt_movesToDeadLetterAtMaxRetry() {
        DividendPayout payout = retryingPayout(2);
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        dividendPayoutWriter.markFailedAttempt(payout.getId());

        assertThat(payout.getRetryCount()).isEqualTo(3);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
    }

    @Test
    @DisplayName("updateBatchStatus: 진행 중인 건이 남아있으면 배치 상태를 바꾸지 않는다")
    void updateBatchStatus_skipsWhenAnyInProgress() {
        SettlementBatch batch = openBatch();
        DividendPayout inProgress = queuedPayout();
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(inProgress));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(result).isEmpty();
        verify(settlementBatchRepository, never()).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: PROCESSING 건이 남아있으면 배치 상태를 바꾸지 않는다")
    void updateBatchStatus_skipsWhenAnyProcessing() {
        SettlementBatch batch = openBatch();
        DividendPayout processing = queuedPayout();
        ReflectionTestUtils.setField(processing, "status", PayoutStatus.PROCESSING);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(processing));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(result).isEmpty();
        verify(settlementBatchRepository, never()).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 전부 성공하면 COMPLETED로 전환하고 배치를 반환한다")
    void updateBatchStatus_marksCompletedWhenAllPaid() {
        SettlementBatch batch = openBatch();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result).contains(batch);
        verify(settlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 일부만 DEAD_LETTER면 PARTIAL_FAILED로 전환하고 배치를 반환한다")
    void updateBatchStatus_marksPartialFailedWhenSomeDeadLetter() {
        SettlementBatch batch = openBatch();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        DividendPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid, deadLetter));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
        assertThat(result).contains(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 전부 DEAD_LETTER면 FAILED로 전환하고 배치를 반환한다")
    void updateBatchStatus_marksFailedWhenAllDeadLetter() {
        SettlementBatch batch = openBatch();
        DividendPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(deadLetter));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result).contains(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 일부는 PAID, 나머지는 전부 ABANDONED면(남은 DEAD_LETTER 없음) CLOSED_ABANDONED로 마감한다 (T5)")
    void updateBatchStatus_marksClosedAbandonedWhenRemainingDeadLetterAllAbandoned() {
        SettlementBatch batch = openBatch();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        DividendPayout abandoned = queuedPayout();
        ReflectionTestUtils.setField(abandoned, "status", PayoutStatus.ABANDONED);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid, abandoned));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.CLOSED_ABANDONED);
        assertThat(result).contains(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: ABANDONED가 있어도 아직 포기 안 된 DEAD_LETTER가 남아있으면 기존처럼 PARTIAL_FAILED로 둔다")
    void updateBatchStatus_keepsPartialFailedWhenDeadLetterRemainsBesideAbandoned() {
        SettlementBatch batch = openBatch();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        DividendPayout abandoned = queuedPayout();
        ReflectionTestUtils.setField(abandoned, "status", PayoutStatus.ABANDONED);
        DividendPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid, abandoned, deadLetter));

        Optional<SettlementBatch> result = dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
        assertThat(result).contains(batch);
    }

    @Test
    @DisplayName("abandonPayout: DEAD_LETTER 건을 ABANDONED로 전환하고 지급 증빙을 저장한다")
    void abandonPayout_marksAbandonedWithReason() {
        DividendPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(deadLetter.getId())).thenReturn(Optional.of(deadLetter));

        DividendPayout result = dividendPayoutWriter.abandonPayout(
                deadLetter.getId(), ResolutionType.BANK_TRANSFER, "BANK-REF-001", "투자자 지갑 영구 폐쇄 확인");

        assertThat(result.getStatus()).isEqualTo(PayoutStatus.ABANDONED);
        assertThat(result.getResolutionType()).isEqualTo(ResolutionType.BANK_TRANSFER);
        assertThat(result.getResolutionReference()).isEqualTo("BANK-REF-001");
        assertThat(result.getResolutionNote()).isEqualTo("투자자 지갑 영구 폐쇄 확인");
        verify(dividendPayoutRepository).save(deadLetter);
    }

    @Test
    @DisplayName("abandonPayout: DEAD_LETTER 상태가 아니면 예외를 던지고 저장하지 않는다")
    void abandonPayout_rejectsWhenNotDeadLetter() {
        DividendPayout queued = queuedPayout();
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(queued.getId())).thenReturn(Optional.of(queued));

        assertThatThrownBy(() -> dividendPayoutWriter.abandonPayout(queued.getId(), ResolutionType.BANK_TRANSFER, "REF", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.PAYOUT_NOT_ABANDONABLE);

        assertThat(queued.getStatus()).isEqualTo(PayoutStatus.QUEUED);
        verify(dividendPayoutRepository, never()).save(any());
    }

    private SettlementBatch openBatch() {
        return SettlementBatch.open(ASSET_ID, UUID.randomUUID(), RECORD_DATE, 1_000_000L);
    }

    private DividendPayout queuedPayout() {
        return DividendPayout.queue(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
    }

    private DividendPayout retryingPayout(int retryCount) {
        DividendPayout payout = queuedPayout();
        ReflectionTestUtils.setField(payout, "status", PayoutStatus.RETRYING);
        ReflectionTestUtils.setField(payout, "retryCount", retryCount);
        return payout;
    }
}