package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisbursementRetryScheduler {

    private static final List<PayoutStatus> RESUMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);
    private static final long RETRY_INTERVAL_MS = 3 * 60 * 1000L;
    private static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofMinutes(5);

    private final DividendPayoutRepository dividendPayoutRepository;
    private final DividendDisbursementService dividendDisbursementService;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    public void retryStuckDividendPayouts() {
        reclaimStalledDividendProcessing();

        List<UUID> settlementBatchIds = dividendPayoutRepository.findDistinctSettlementBatchIdByStatusIn(RESUMABLE_STATUSES);
        if (settlementBatchIds.isEmpty()) {
            return;
        }
        log.info("QUEUED/RETRYING 배당 지급 건이 남은 정산 회차 {}건을 재트리거합니다: {}", settlementBatchIds.size(), settlementBatchIds);
        settlementBatchIds.forEach(dividendDisbursementService::disburseAsync);
    }

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    public void retryStuckFinalSettlementPayouts() {
        reclaimStalledFinalSettlementProcessing();

        List<UUID> finalSettlementBatchIds =
                finalSettlementPayoutRepository.findDistinctFinalSettlementBatchIdByStatusIn(RESUMABLE_STATUSES);
        if (finalSettlementBatchIds.isEmpty()) {
            return;
        }
        log.info("QUEUED/RETRYING 최종 정산 지급 건이 남은 회차 {}건을 재트리거합니다: {}", finalSettlementBatchIds.size(), finalSettlementBatchIds);
        finalSettlementBatchIds.forEach(finalSettlementDisbursementService::disburseAsync);
    }

    private void reclaimStalledDividendProcessing() {
        Instant staleBefore = Instant.now().minus(STALE_PROCESSING_THRESHOLD);
        int reclaimed = dividendDisbursementService.reclaimStalledProcessing(staleBefore);
        if (reclaimed > 0) {
            log.warn("PROCESSING 상태로 {}분 이상 멈춰있던 배당 지급 건 {}건을 QUEUED로 복구했습니다.",
                    STALE_PROCESSING_THRESHOLD.toMinutes(), reclaimed);
        }
    }

    private void reclaimStalledFinalSettlementProcessing() {
        Instant staleBefore = Instant.now().minus(STALE_PROCESSING_THRESHOLD);
        int reclaimed = finalSettlementDisbursementService.reclaimStalledProcessing(staleBefore);
        if (reclaimed > 0) {
            log.warn("PROCESSING 상태로 {}분 이상 멈춰있던 최종 정산 지급 건 {}건을 QUEUED로 복구했습니다.",
                    STALE_PROCESSING_THRESHOLD.toMinutes(), reclaimed);
        }
    }
}