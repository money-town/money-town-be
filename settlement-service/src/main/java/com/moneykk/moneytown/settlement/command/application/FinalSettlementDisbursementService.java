package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalSettlementDisbursementService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> PENDING_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    private final WalletServiceClient walletServiceClient;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID finalSettlementBatchId) {
        disburse(finalSettlementBatchId);
    }

    @Transactional
    public void disburse(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = finalSettlementBatchRepository.findByIdAndIsDeletedFalse(finalSettlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));
        batch.markDisbursing();

        List<FinalSettlementPayout> pendingPayouts = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(finalSettlementBatchId, PENDING_STATUSES);

        pendingPayouts.forEach(payout -> attempt(batch, payout));
        finalSettlementPayoutRepository.saveAll(pendingPayouts);

        updateBatchStatus(batch);
        finalSettlementBatchRepository.save(batch);
    }

    private void attempt(FinalSettlementBatch batch, FinalSettlementPayout payout) {
        try {
            walletServiceClient.depositSettlement(new SettlementDepositRequest(
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

    private void updateBatchStatus(FinalSettlementBatch batch) {
        List<FinalSettlementPayout> allPayouts =
                finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batch.getId());

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