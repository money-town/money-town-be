package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import feign.FeignException;
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

        List<DividendPayout> pendingPayouts = payoutWriter.findPendingPayouts(settlementBatchId);
        pendingPayouts.forEach(payout -> attempt(settlementBatchId, payout));

        payoutWriter.updateBatchStatus(settlementBatchId);
    }

    private void attempt(UUID settlementBatchId, DividendPayout payout) {
        try {
            walletServiceClient.depositDividend(new DividendDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), settlementBatchId, payout.getAmount()));
            payoutWriter.markPaid(payout.getId());
        } catch (FeignException e) {
            payoutWriter.markFailedAttempt(payout.getId());
        }
    }
}