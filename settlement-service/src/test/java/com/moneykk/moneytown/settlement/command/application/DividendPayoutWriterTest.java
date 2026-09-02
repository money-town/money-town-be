package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendPayoutWriterTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;

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
    @DisplayName("markResponseMismatch: retryCount와 무관하게 즉시 DEAD_LETTER로 전환한다")
    void marksResponseMismatch() {
        DividendPayout payout = retryingPayout(2);
        when(dividendPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).thenReturn(Optional.of(payout));

        dividendPayoutWriter.markResponseMismatch(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.DEAD_LETTER);
        assertThat(payout.getRetryCount()).isEqualTo(2);
        verify(dividendPayoutRepository).save(payout);
    }

    @Test
    @DisplayName("updateBatchStatus: 진행 중인 건이 남아있으면 배치 상태를 바꾸지 않는다")
    void updateBatchStatus_skipsWhenAnyInProgress() {
        SettlementBatch batch = openBatch();
        DividendPayout inProgress = queuedPayout();
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(inProgress));

        dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PENDING);
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

        dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PENDING);
        verify(settlementBatchRepository, never()).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 전부 성공하면 COMPLETED로 전환한다")
    void updateBatchStatus_marksCompletedWhenAllPaid() {
        SettlementBatch batch = openBatch();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid));

        dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        verify(settlementBatchRepository).save(batch);
    }

    @Test
    @DisplayName("updateBatchStatus: 일부만 DEAD_LETTER면 PARTIAL_FAILED로 전환한다")
    void updateBatchStatus_marksPartialFailedWhenSomeDeadLetter() {
        SettlementBatch batch = openBatch();
        DividendPayout paid = queuedPayout();
        ReflectionTestUtils.setField(paid, "status", PayoutStatus.PAID);
        DividendPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(paid, deadLetter));

        dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("updateBatchStatus: 전부 DEAD_LETTER면 FAILED로 전환한다")
    void updateBatchStatus_marksFailedWhenAllDeadLetter() {
        SettlementBatch batch = openBatch();
        DividendPayout deadLetter = queuedPayout();
        ReflectionTestUtils.setField(deadLetter, "status", PayoutStatus.DEAD_LETTER);
        when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId()))
                .thenReturn(List.of(deadLetter));

        dividendPayoutWriter.updateBatchStatus(batch.getId());

        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    private SettlementBatch openBatch() {
        return SettlementBatch.open(ASSET_ID, UUID.randomUUID(), RECORD_DATE, 1_000_000L, 0L);
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