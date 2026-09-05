package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinalSettlementDisbursementService {

    private final FinalSettlementPayoutWriter payoutWriter;
    private final WalletServiceClient walletServiceClient;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID finalSettlementBatchId) {
        disburse(finalSettlementBatchId);
    }

    public void disburse(UUID finalSettlementBatchId) {
        payoutWriter.markDisbursing(finalSettlementBatchId);

        List<FinalSettlementPayout> claimedPayouts = payoutWriter.claimPendingPayouts(finalSettlementBatchId);
        claimedPayouts.forEach(payout -> attempt(finalSettlementBatchId, payout));

        payoutWriter.updateBatchStatus(finalSettlementBatchId);
    }

    public int reclaimStalledProcessing(Instant staleBefore) {
        return payoutWriter.reclaimStalledProcessing(staleBefore);
    }

    private void attempt(UUID finalSettlementBatchId, FinalSettlementPayout payout) {
        try {
            ApiResponse<SettlementDepositResponse> response = walletServiceClient.depositSettlement(new SettlementDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), finalSettlementBatchId, payout.getAmount()));
            if (!response.success()) {
                payoutWriter.markFailedAttempt(payout.getId());
                return;
            }

            SettlementDepositResponse data = response.data();
            if (data == null || !finalSettlementBatchId.equals(data.finalSettlementBatchId())) {
                // TODO 도전 기능 = 별도 상태/플래그를 둬서 재처리 API가 구분 -> 지갑 트랜잭션 대조 확인 후에만 재처리
                // 일반 DEAD_LETTER와 같은 재처리 경로(retryFinalSettlement) -> 사람이 로그를 못 보고 재처리 버튼을 누르면 대조 확인 없이 재시도 가능
                log.error("지갑 응답의 finalSettlementBatchId가 요청과 다릅니다 — 재처리 전 지갑 트랜잭션 대조 확인 필요. "
                                + "payoutId={}, 요청 finalSettlementBatchId={}, 응답 finalSettlementBatchId={}, transactionId={}",
                        payout.getId(), finalSettlementBatchId, data == null ? null : data.finalSettlementBatchId(),
                        data == null ? null : data.transactionId());
                payoutWriter.markResponseMismatch(payout.getId());
                return;
            }

            payoutWriter.markPaid(payout.getId());
        } catch (Exception e) {
            payoutWriter.markFailedAttempt(payout.getId());
        }
    }
}