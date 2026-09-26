package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
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
    private final SettlementFailureNotifier settlementFailureNotifier;

    @Async("disbursementTaskExecutor")
    public void disburseAsync(UUID settlementBatchId) {
        disburse(settlementBatchId);
    }

    // claim + Outbox 저장까지만 한다. 실제 지급은 Outbox → Kafka → 지갑이 처리하고, 그 결과를
    // applyDispatchResult로 돌려받는다(정산은 더 이상 지갑을 직접 호출하지 않음).
    public void disburse(UUID settlementBatchId) {
        if (!payoutWriter.markDisbursing(settlementBatchId)) {
            log.warn("이미 종결된 정산 회차라 지급 처리를 건너뜁니다 (settlementBatchId={}). 남은 QUEUED/RETRYING 지급 건이 있다면 데이터 정합성을 확인하세요.",
                    settlementBatchId);
            return;
        }

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

    // 지갑 결과 컨슈머 진입점 — payout 1건에 지갑이 알려준 결과를 반영 + 회차 마감 확인
    // 지갑을 더 이상 직접 부르지 않으므로 attempt()의 예외 처리(FeignException 등)는 필요X
    // 여기서 던지는 예외는 역직렬화 실패 같은 시스템 오류뿐이고, Kafka 재시도 후 DLT로 간다.
    public void applyDispatchResult(UUID payoutId, UUID settlementBatchId, boolean succeeded, String reason) {
        if (payoutWriter.isProcessing(payoutId)) {
            if (succeeded) {
                payoutWriter.markPaid(payoutId);
            } else {
                log.warn("배당 지급 실패 결과 수신. payoutId={}, reason={}", payoutId, reason);
                payoutWriter.markFailedAttempt(payoutId);
            }
        } else {
            log.warn("이미 처리됐거나 회수된 배당 지급 건의 결과를 건너뜁니다 (중복 수신 등). payoutId={}", payoutId);
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
}