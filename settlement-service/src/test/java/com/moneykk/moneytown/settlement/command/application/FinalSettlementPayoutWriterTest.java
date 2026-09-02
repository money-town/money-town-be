package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalSettlementPayoutWriterTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final Instant TERMINATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Long UNIT_PRICE = 1_000_000L;

    @Mock
    private FinalSettlementBatchRepository finalSettlementBatchRepository;
    @Mock
    private FinalSettlementPayoutRepository finalSettlementPayoutRepository;

    @InjectMocks
    private FinalSettlementPayoutWriter finalSettlementPayoutWriter;

    @Test
    @DisplayName("markDisbursing: 배치를 DISBURSING으로 전환하고 저장한다")
    void marksDisbursing() {
        FinalSettlementBatch batch = calculatedBatch();
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));

        finalSettlementPayoutWriter.markDisbursing(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.DISBURSING);
        verify(finalSettlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("markDisbursing: 존재하지 않는 배치면 예외")
    void marksDisbursing_batchNotFound() {
        UUID unknownBatchId = UUID.randomUUID();
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(unknownBatchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finalSettlementPayoutWriter.markDisbursing(unknownBatchId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND);
    }

    @Test
    @DisplayName("claimPendingPayouts: QUEUED/RETRYING 건을 PROCESSING으로 전환해 선점하고 반환한다")
    void claimsPendingPayoutsAndMarksProcessing() {
        UUID batchId = UUID.randomUUID();
        FinalSettlementPayout queued = queuedPayout();
        FinalSettlementPayout retrying = retryingPayout(1);
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING)))
                .thenReturn(List.of(queued, retrying));

        List<FinalSettlementPayout> claimed = finalSettlementPayoutWriter.claimPendingPayouts(batchId);

        assertThat(claimed).containsExactly(queued, retrying);
        assertThat(queued.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(retrying.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
        verify(finalSettlementPayoutRepository).saveAll(claimed);
    }

    @Test
    @DisplayName("reclaimStalledProcessing: 기준 시각 이전에 멈춘 PROCESSING 건을 QUEUED로 되돌리되 retryCount는 보존한다")
    void reclaimsStalledProcessing() {
        FinalSettlementPayout stalled = retryingPayout(1);
        ReflectionTestUtils.setField(stalled, "status", PayoutStatus.PROCESSING);
        Instant staleBefore = Instant.now();
        when(finalSettlementPayoutRepository.findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, staleBefore))
                .thenReturn(List.of(stalled));

        int reclaimed = finalSettlementPayoutWriter.reclaimStalledProcessing(staleBefore);

        assertThat(reclaimed).isEqualTo(1);
        assertThat(stalled.getStatus()).isEqualTo(PayoutStatus.QUEUED);
        assertThat(stalled.getRetryCount()).isEqualTo(1);
        verify(finalSettlementPayoutRepository).saveAll(List.of(stalled));
    }

    @Test
    @DisplayName("markPaid: 지급 건을 PAID로 전환하고 저장한다")
    void marksPaid() {
        FinalSettlementPayout payout = queuedPayout();
        when(finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        finalSettlementPayoutWriter.markPaid(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        verify(finalSettlementPayoutRepository).save(payout);
    }

    @Test
    @DisplayName("markResponseMismatch: retryCount와 무관하게 즉시 DEAD_LETTER로 전환한다")
    void marksResponseMismatch() {
        FinalSettlementPayout payout = queuedPayout();
        when(finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        finalSettlementPayoutWriter.markResponseMismatch(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
        assertThat(payout.getRetryCount()).isZero();
        verify(finalSettlementPayoutRepository).save(payout);
    }

    @Test
    @DisplayName("markFailedAttempt: 최대 재시도 미만이면 retryCount만 올리고 RETRYING으로 남긴다")
    void marksFailedAttempt_belowMaxRetry() {
        FinalSettlementPayout payout = queuedPayout();
        when(finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        finalSettlementPayoutWriter.markFailedAttempt(payout.getId());

        assertThat(payout.getRetryCount()).isEqualTo(1);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.RETRYING);
        verify(finalSettlementPayoutRepository).save(payout);
    }

    @Test
    @DisplayName("markFailedAttempt: 3회째 실패하면 DEAD_LETTER로 전환한다")
    void marksFailedAttempt_movesToDeadLetterAtMaxRetry() {
        FinalSettlementPayout payout = retryingPayout(2);
        when(finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        finalSettlementPayoutWriter.markFailedAttempt(payout.getId());

        assertThat(payout.getRetryCount()).isEqualTo(3);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
    }

    @Test
    @DisplayName("updateBatchStatus: 진행 중인 건이 남아있으면 배치 상태를 바꾸지 않는다")
    void updateBatchStatus_skipsWhenAnyInProgress() {
        FinalSettlementBatch batch = calculatedBatch();
        FinalSettlementPayout inProgress = queuedPayout();
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(inProgress));

        finalSettlementPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        verify(finalSettlementBatchRepository, never()).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: PROCESSING 건이 남아있으면 배치 상태를 바꾸지 않는다")
    void updateBatchStatus_skipsWhenAnyProcessing() {
        FinalSettlementBatch batch = calculatedBatch();
        FinalSettlementPayout processing = queuedPayout();
        ReflectionTestUtils.setField(processing, "status", PayoutStatus.PROCESSING);
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(processing));

        finalSettlementPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        verify(finalSettlementBatchRepository, never()).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 전부 성공하면 COMPLETED로 전환한다")
    void updateBatchStatus_marksCompletedWhenAllPaid() {
        FinalSettlementBatch batch = calculatedBatch();
        FinalSettlementPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid));

        finalSettlementPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        verify(finalSettlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 일부만 DEAD_LETTER면 PARTIAL_FAILED로 전환한다")
    void updateBatchStatus_marksPartialFailedWhenSomeDeadLetter() {
        FinalSettlementBatch batch = calculatedBatch();
        FinalSettlementPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        FinalSettlementPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid, deadLetter));

        finalSettlementPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("updateBatchStatus: 전부 DEAD_LETTER면 FAILED로 전환한다")
    void updateBatchStatus_marksFailedWhenAllDeadLetter() {
        FinalSettlementBatch batch = calculatedBatch();
        FinalSettlementPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(deadLetter));

        finalSettlementPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    private FinalSettlementBatch calculatedBatch() {
        FinalSettlementBatch batch = FinalSettlementBatch.open(ASSET_ID, TERMINATED_AT, UNIT_PRICE, 900_000_000L);
        batch.markCalculated();
        return batch;
    }

    private FinalSettlementPayout queuedPayout() {
        return FinalSettlementPayout.queue(UUID.randomUUID(), UUID.randomUUID(), 900L, 1_000_000L);
    }

    private FinalSettlementPayout retryingPayout(int retryCount) {
        FinalSettlementPayout payout = queuedPayout();
        ReflectionTestUtils.setField(payout, "status", PayoutStatus.RETRYING);
        ReflectionTestUtils.setField(payout, "retryCount", retryCount);
        return payout;
    }
}