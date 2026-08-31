package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalSettlementDisbursementService {

    private final FinalSettlementPayoutWriter payoutWriter;
    private final WalletServiceClient walletServiceClient;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID finalSettlementBatchId) {
        disburse(finalSettlementBatchId);
    }

    public void disburse(UUID finalSettlementBatchId) {
        payoutWriter.markDisbursing(finalSettlementBatchId);

        List<FinalSettlementPayout> pendingPayouts = payoutWriter.findPendingPayouts(finalSettlementBatchId);
        pendingPayouts.forEach(payout -> attempt(finalSettlementBatchId, payout));

        payoutWriter.updateBatchStatus(finalSettlementBatchId);
    }

    private void attempt(UUID finalSettlementBatchId, FinalSettlementPayout payout) {
        try {
            ApiResponse<SettlementDepositResponse> response = walletServiceClient.depositSettlement(new SettlementDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), finalSettlementBatchId, payout.getAmount()));
            if (response.success()) {
                payoutWriter.markPaid(payout.getId());
            } else {
                payoutWriter.markFailedAttempt(payout.getId());
            }
        } catch (FeignException e) {
            payoutWriter.markFailedAttempt(payout.getId());
        }
    }
}