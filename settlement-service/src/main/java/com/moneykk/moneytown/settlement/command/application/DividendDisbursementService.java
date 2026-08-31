package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DividendDisbursementService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> PENDING_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;
    private final WalletServiceClient walletServiceClient;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID settlementBatchId) {
        disburse(settlementBatchId);
    }

    @Transactional
    public void disburse(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));
        batch.markDisbursing();

        List<DividendPayout> pendingPayouts = dividendPayoutRepository
                .findBySettlementBatchIdAndStatusInAndIsDeletedFalse(settlementBatchId, PENDING_STATUSES);

        pendingPayouts.forEach(payout -> attempt(batch, payout));
        dividendPayoutRepository.saveAll(pendingPayouts);

        updateBatchStatus(batch);
        settlementBatchRepository.save(batch);
    }

    private void attempt(SettlementBatch batch, DividendPayout payout) {
        try {
            walletServiceClient.depositDividend(new DividendDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), batch.getId(), payout.getAmount()));
            payout.markPaid();
        } catch (FeignException e) {
            payout.incrementRetryCount();
            if (payout.getRetryCount() >= MAX_RETRY_COUNT) {
                payout.markDeadLetter();
            } else {
                payout.markRetrying();
            }
        }
    }

    private void updateBatchStatus(SettlementBatch batch) {
        List<DividendPayout> allPayouts = dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batch.getId());

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
    }
}