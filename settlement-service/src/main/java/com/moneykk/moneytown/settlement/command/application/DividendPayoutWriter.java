package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class DividendPayoutWriter {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> CLAIMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);
    private static final List<PayoutStatus> IN_PROGRESS_STATUSES =
            List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING, PayoutStatus.PROCESSING);

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    @Transactional
    public void markDisbursing(UUID settlementBatchId) {
        SettlementBatch batch = loadBatch(settlementBatchId);
        batch.markDisbursing();
        settlementBatchRepository.save(batch);
    }

    @Transactional
    public List<DividendPayout> claimPendingPayouts(UUID settlementBatchId) {
        List<DividendPayout> claimed = dividendPayoutRepository
                .findBySettlementBatchIdAndStatusInAndIsDeletedFalse(settlementBatchId, CLAIMABLE_STATUSES);
        claimed.forEach(DividendPayout::markProcessing);
        dividendPayoutRepository.saveAll(claimed);
        return claimed;
    }

    @Transactional
    public int reclaimStalledProcessing(Instant staleBefore) {
        List<DividendPayout> stalled = dividendPayoutRepository
                .findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, staleBefore);
        stalled.forEach(DividendPayout::revertStalledProcessing);
        dividendPayoutRepository.saveAll(stalled);
        return stalled.size();
    }

    @Transactional
    public void markPaid(UUID payoutId) {
        DividendPayout payout = loadPayout(payoutId);
        payout.markPaid();
        dividendPayoutRepository.save(payout);
    }

    @Transactional
    public void markFailedAttempt(UUID payoutId) {
        DividendPayout payout = loadPayout(payoutId);
        payout.incrementRetryCount();
        if (payout.getRetryCount() >= MAX_RETRY_COUNT) {
            payout.markDeadLetter();
        } else {
            payout.markRetrying();
        }
        dividendPayoutRepository.save(payout);
    }

    @Transactional
    public void updateBatchStatus(UUID settlementBatchId) {
        SettlementBatch batch = loadBatch(settlementBatchId);
        List<DividendPayout> allPayouts = dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(settlementBatchId);

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
        settlementBatchRepository.save(batch);
    }

    private SettlementBatch loadBatch(UUID settlementBatchId) {
        return settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));
    }

    private DividendPayout loadPayout(UUID payoutId) {
        return dividendPayoutRepository.findByIdAndIsDeletedFalse(payoutId)
                .orElseThrow(() -> new IllegalStateException("지급 건을 찾을 수 없습니다: " + payoutId));
    }
}