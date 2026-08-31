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

import java.util.List;
import java.util.UUID;

/**
 * QUEUED/RETRYING 상태로 멈춰있는 지급 건을 주기적으로 재트리거한다(앱 재시작 없이도 복구).
 * disbursementTaskExecutor(core/max pool size 1)를 통해 disburseAsync를 호출하므로 이
 * 인스턴스 안에서는 실행이 항상 직렬화된다. 다만 인스턴스가 여러 대로 늘어나면 같은 배치를
 * 두 인스턴스가 동시에 집을 수 있으므로, 그 시점에는 ShedLock 같은 분산 락 도입이 필요하다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DisbursementRetryScheduler {

    private static final List<PayoutStatus> RESUMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);
    private static final long RETRY_INTERVAL_MS = 3 * 60 * 1000L;

    private final DividendPayoutRepository dividendPayoutRepository;
    private final DividendDisbursementService dividendDisbursementService;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    public void retryStuckDividendPayouts() {
        List<UUID> settlementBatchIds = dividendPayoutRepository.findDistinctSettlementBatchIdByStatusIn(RESUMABLE_STATUSES);
        if (settlementBatchIds.isEmpty()) {
            return;
        }
        log.info("QUEUED/RETRYING 배당 지급 건이 남은 정산 회차 {}건을 재트리거합니다: {}", settlementBatchIds.size(), settlementBatchIds);
        settlementBatchIds.forEach(dividendDisbursementService::disburseAsync);
    }

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    public void retryStuckFinalSettlementPayouts() {
        List<UUID> finalSettlementBatchIds =
                finalSettlementPayoutRepository.findDistinctFinalSettlementBatchIdByStatusIn(RESUMABLE_STATUSES);
        if (finalSettlementBatchIds.isEmpty()) {
            return;
        }
        log.info("QUEUED/RETRYING 최종 정산 지급 건이 남은 회차 {}건을 재트리거합니다: {}", finalSettlementBatchIds.size(), finalSettlementBatchIds);
        finalSettlementBatchIds.forEach(finalSettlementDisbursementService::disburseAsync);
    }
}