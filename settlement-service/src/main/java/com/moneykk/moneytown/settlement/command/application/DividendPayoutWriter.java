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

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class DividendPayoutWriter {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> PENDING_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    @Transactional
    public void markDisbursing(UUID settlementBatchId) {
        SettlementBatch batch = loadBatch(settlementBatchId);
        batch.markDisbursing();
        settlementBatchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public List<DividendPayout> findPendingPayouts(UUID settlementBatchId) {
        return dividendPayoutRepository
                .findBySettlementBatchIdAndStatusInAndIsDeletedFalse(settlementBatchId, PENDING_STATUSES);
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
                .anyMatch(payout -> PENDING_STATUSES.contains(payout.getStatus()));
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