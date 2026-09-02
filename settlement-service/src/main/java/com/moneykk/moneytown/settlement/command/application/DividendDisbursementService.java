package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositResponse;
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

    public int reclaimStalledProcessing(Instant staleBefore) {
        return payoutWriter.reclaimStalledProcessing(staleBefore);
    }

    private void attempt(UUID settlementBatchId, DividendPayout payout) {
        try {
            ApiResponse<DividendDepositResponse> response = walletServiceClient.depositDividend(new DividendDepositRequest(
                    payout.getId().toString(), payout.getInvestorId(), settlementBatchId, payout.getAmount()));
            if (!response.success()) {
                payoutWriter.markFailedAttempt(payout.getId());
                return;
            }

            DividendDepositResponse data = response.data();
            if (data == null || !settlementBatchId.equals(data.settlementBatchId())) {
                // TODO: 도전 기능 = 별도 상태/플래그를 둬서 재처리 API가 이 건을 구분 -> 지갑 트랜잭션 대조 확인 후에만 재처리 가능
                // 일반 DEAD_LETTER와 같은 재처리 경로(retryBatch) -> 사람이 로그를 못 보고 재처리 버튼을 누르면 대조 확인 없이 재시도 가능
                log.error("지갑 응답의 settlementBatchId가 요청과 다릅니다 — 재처리 전 지갑 트랜잭션 대조 확인 필요. "
                                + "payoutId={}, 요청 settlementBatchId={}, 응답 settlementBatchId={}, transactionId={}",
                        payout.getId(), settlementBatchId, data == null ? null : data.settlementBatchId(),
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