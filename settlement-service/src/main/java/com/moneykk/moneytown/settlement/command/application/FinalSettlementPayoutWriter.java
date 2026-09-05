package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FinalSettlementPayoutWriter {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> CLAIMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);
    private static final List<PayoutStatus> IN_PROGRESS_STATUSES =
            List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING, PayoutStatus.PROCESSING);

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;

    @Transactional
    public void markDisbursing(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        batch.markDisbursing();
        finalSettlementBatchRepository.save(batch);
    }

    @Transactional
    public List<FinalSettlementPayout> claimPendingPayouts(UUID finalSettlementBatchId) {
        List<FinalSettlementPayout> claimed = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(finalSettlementBatchId, CLAIMABLE_STATUSES);
        claimed.forEach(FinalSettlementPayout::markProcessing);
        finalSettlementPayoutRepository.saveAll(claimed);
        return claimed;
    }

    @Transactional
    public int reclaimStalledProcessing(Instant staleBefore) {
        List<FinalSettlementPayout> stalled = finalSettlementPayoutRepository
                .findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, staleBefore);
        stalled.forEach(FinalSettlementPayout::revertStalledProcessing);
        finalSettlementPayoutRepository.saveAll(stalled);
        return stalled.size();
    }

    @Transactional
    public void markPaid(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.markPaid();
        finalSettlementPayoutRepository.save(payout);
    }

    @Transactional
    public void markFailedAttempt(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.incrementRetryCount();
        if (payout.getRetryCount() >= MAX_RETRY_COUNT) {
            payout.markDeadLetter();
        } else {
            payout.markRetrying();
        }
        finalSettlementPayoutRepository.save(payout);
    }

    // 지갑 응답이 success=true인데 우리가 보낸 finalSettlementBatchId와 다른 값을 돌려준 경우 : 즉시 DEAD_LETTER
    @Transactional
    public void markResponseMismatch(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.markDeadLetter();
        finalSettlementPayoutRepository.save(payout);
    }

    @Transactional
    public void updateBatchStatus(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        List<FinalSettlementPayout> allPayouts =
                finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(finalSettlementBatchId);

        boolean anyInProgress = allPayouts.stream()
                .anyMatch(payout -> IN_PROGRESS_STATUSES.contains(payout.getStatus()));
        if (anyInProgress) {
            return;
        }

        boolean anyDeadLetter = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.DEAD_LETTER);
        boolean anyPaid = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.PAID);

        if (!anyDeadLetter) {
            batch.markCompleted();
        } else if (anyPaid) {
            batch.markPartialFailed();
        } else {
            batch.markFailed();
        }
        finalSettlementBatchRepository.save(batch);
    }

    private FinalSettlementBatch loadBatch(UUID finalSettlementBatchId) {
        return finalSettlementBatchRepository.findByIdAndIsDeletedFalse(finalSettlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));
    }

    private FinalSettlementPayout loadPayout(UUID payoutId) {
        return finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payoutId)
                .orElseThrow(() -> new IllegalStateException("지급 건을 찾을 수 없습니다: " + payoutId));
    }
}