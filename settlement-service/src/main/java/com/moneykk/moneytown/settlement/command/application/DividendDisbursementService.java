package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DividendDisbursementService {

    private final DividendPayoutWriter payoutWriter;
    private final WalletServiceClient walletServiceClient;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID settlementBatchId) {
        disburse(settlementBatchId);
    }

    public void disburse(UUID settlementBatchId) {
        payoutWriter.markDisbursing(settlementBatchId);

        List<DividendPayout> claimedPayouts = payoutWriter.claimPendingPayouts(settlementBatchId);
        claimedPayouts.forEach(payout -> attempt(settlementBatchId, payout));

        payoutWriter.updateBatchStatus(settlementBatchId);
    }

    private void attempt(UUID settlementBatchId, DividendPayout payout) {
        try {
            ApiResponse<DividendDepositResponse> response = walletServiceClient.depositDividend(new DividendDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), settlementBatchId, payout.getAmount()));
            if (response.success()) {
                payoutWriter.markPaid(payout.getId());
            } else {
                payoutWriter.markFailedAttempt(payout.getId());
            }
        } catch (Exception e) {
            payoutWriter.markFailedAttempt(payout.getId());
        }
    }
}