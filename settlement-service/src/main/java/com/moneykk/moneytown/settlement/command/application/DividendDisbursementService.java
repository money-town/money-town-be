package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
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

    private static final List<SettlementStatus> FAILURE_STATUSES = List.of(SettlementStatus.FAILED, SettlementStatus.PARTIAL_FAILED);

    private final DividendPayoutWriter payoutWriter;
    private final WalletServiceClient walletServiceClient;
    private final SettlementFailureNotifier settlementFailureNotifier;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID settlementBatchId) {
        disburse(settlementBatchId);
    }

    // claim + Outbox 저장까지만 한다. 실제 지급(attempt)은 Outbox → Kafka → 컨슈머가 payout 1건씩 처리한다.
    public void disburse(UUID settlementBatchId) {
        payoutWriter.markDisbursing(settlementBatchId);

        long claimStartedAt = System.nanoTime();
        List<DividendPayout> claimedPayouts = payoutWriter.claimPendingPayouts(settlementBatchId);
        long claimMillis = (System.nanoTime() - claimStartedAt) / 1_000_000;
        log.info("배당 지급 처리 시작 (settlementBatchId={}, 대상 건수={}, claim+Outbox 저장 소요={}ms)",
                settlementBatchId, claimedPayouts.size(), claimMillis);

        if (claimedPayouts.isEmpty()) {
            // 메시지가 없으면 컨슈머가 마감 판정을 트리거하지 않으므로 여기서 직접 판정한다
            finalizeBatchIfDone(settlementBatchId);
        }
    }

    // 컨슈머 진입점 — payout 1건 처리 + 회차 마감 확인
    public void processDispatchedPayout(UUID payoutId, UUID settlementBatchId, UUID investorId, Long amount) {
        if (payoutWriter.isProcessing(payoutId)) {
            attempt(settlementBatchId, payoutId, investorId, amount);
        } else {
            log.warn("이미 처리됐거나 회수된 배당 지급 건의 메시지를 건너뜁니다 (중복 수신 등). payoutId={}", payoutId);
        }
        finalizeBatchIfDone(settlementBatchId);
    }

    private void finalizeBatchIfDone(UUID settlementBatchId) {
        if (payoutWriter.hasInProgressPayouts(settlementBatchId)) {
            return;
        }
        payoutWriter.finalizeBatchIfDisbursing(settlementBatchId).ifPresent(batch -> {
            log.info("배당 지급 처리 마감 (settlementBatchId={}, status={})", settlementBatchId, batch.getStatus());
            if (FAILURE_STATUSES.contains(batch.getStatus())) {
                settlementFailureNotifier.notifyDividendBatchFailed(batch);
            }
        });
    }

    public int reclaimStalledProcessing(Instant staleBefore) {
        return payoutWriter.reclaimStalledProcessing(staleBefore);
    }

    private void attempt(UUID settlementBatchId, UUID payoutId, UUID investorId, Long amount) {
        try {
            ApiResponse<DividendDepositResponse> response = walletServiceClient.depositDividend(new DividendDepositRequest(
                    payoutId.toString(), investorId, settlementBatchId, amount));
            if (!response.success()) {
                payoutWriter.markFailedAttempt(payoutId);
                return;
            }

            DividendDepositResponse data = response.data();
            if (data == null || !settlementBatchId.equals(data.settlementBatchId())) {
                // TODO: 도전 기능 = 별도 상태/플래그를 둬서 재처리 API가 이 건을 구분 -> 지갑 트랜잭션 대조 확인 후에만 재처리 가능
                // 일반 DEAD_LETTER와 같은 재처리 경로(retryBatch) -> 사람이 로그를 못 보고 재처리 버튼을 누르면 대조 확인 없이 재시도 가능
                log.error("지갑 응답의 settlementBatchId가 요청과 다릅니다 — 재처리 전 지갑 트랜잭션 대조 확인 필요. "
                                + "payoutId={}, 요청 settlementBatchId={}, 응답 settlementBatchId={}, transactionId={}",
                        payoutId, settlementBatchId, data == null ? null : data.settlementBatchId(),
                        data == null ? null : data.transactionId());
                payoutWriter.markResponseMismatch(payoutId);
                return;
            }

            payoutWriter.markPaid(payoutId);
        } catch (Exception e) {
            log.error("배당 지급 처리 중 예외 발생. payoutId={}", payoutId, e);
            payoutWriter.markFailedAttempt(payoutId);
        }
    }
}