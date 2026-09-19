package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.ReadyRevenueListResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@ConditionalOnProperty(
        name = "settlement.scheduler.revenue-polling.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Component
@RequiredArgsConstructor
@Slf4j
public class RevenuePollingScheduler {

    private static final long POLL_INTERVAL_MS = 30 * 60 * 1000L;
    private static final int MAX_PAGES = 1000;
    private static final String SYSTEM_ROLE = "SYSTEM";

    // 폴링 중 자연스럽게 발생할 수 있는, 재시도가 필요 없는 상태 — 경고 없이 건너뛴다.
    // SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE는 openBatchAutomatically가 멱등하게
    // 바뀌면서더 이상 예외로 던져지지 않는다 — 이제 여기 남겨두면 진짜 장애를 조용히 삼킬 위험만 있음
    private static final Set<SettlementErrorCode> EXPECTED_SKIP_REASONS = Set.of(
            SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET
    );

    private final AssetServiceClient assetServiceClient;
    private final SettlementCommandService settlementCommandService;
    private final RevenueTransferStatusNotifier revenueTransferStatusNotifier;
    private final DividendDisbursementService dividendDisbursementService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void pollReadyRevenues() {
        UUID cursor = null;
        boolean hasNext = true;
        int pageCount = 0;
        int processedCount = 0;

        while (hasNext) {
            if (++pageCount > MAX_PAGES) {
                log.warn("정산 대기 수익 폴링이 {}페이지를 넘어서 이번 주기는 중단합니다.", MAX_PAGES);
                return;
            }

            UUID requestCursor = cursor;
            ReadyRevenueListResponse page = assetServiceClient.getReadyRevenues(SYSTEM_ROLE, requestCursor).data();

            processedCount += page.revenues().size();
            page.revenues().forEach(this::tryOpenBatch);

            hasNext = page.hasNext();
            UUID nextCursor = page.nextCursor();
            if (hasNext && Objects.equals(nextCursor, requestCursor)) {
                log.warn("정산 대기 수익 폴링의 페이지네이션이 정체되어 이번 주기는 중단합니다.");
                return;
            }
            cursor = nextCursor;
        }

        // 백스톱 스케줄러라 처리한 게 없는 조용한 주기까지 남기지 않는다.
        if (processedCount > 0) {
            log.info("정산 대기 수익 폴링 완료 (처리 시도 건수={})", processedCount);
        }
    }

    private void tryOpenBatch(RevenueResponse revenue) {
        try {
            SettlementBatchResponse response =
                    settlementCommandService.openBatchAutomatically(revenue.assetId(), revenue.revenueId());
            // newlyCreated는 게이트가 아니라 관측 전용이다 — 기존 배치를 재사용한 경우에도 notifyTransferred는 반드시 호출
            // 그렇지 않으면 revenue가 TRANSFERRED로 못 넘어가는 걸 복구할 스케줄러가 없음
            if (!response.newlyCreated()) {
                meterRegistry.counter("settlement.batch.auto_open", "result", "recovered_existing").increment();
            }
            revenueTransferStatusNotifier.notifyTransferred(response.revenueId());
            dividendDisbursementService.disburseAsync(response.settlementBatchId());
        } catch (BusinessException e) {
            if (EXPECTED_SKIP_REASONS.contains(e.getErrorCode())) {
                meterRegistry.counter(
                        "settlement.batch.auto_open",
                        "result",
                        "skipped_in_progress"
                ).increment();
                log.debug("정산 회차 자동 개시 건너뜀 (revenueId={}, reason={})", revenue.revenueId(), e.getErrorCode());
            } else {
                meterRegistry.counter("settlement.batch.auto_open", "result", "failed").increment();
                log.warn("정산 회차 자동 개시 실패 (revenueId={}, reason={})", revenue.revenueId(), e.getErrorCode());
            }
        }
    }
}
