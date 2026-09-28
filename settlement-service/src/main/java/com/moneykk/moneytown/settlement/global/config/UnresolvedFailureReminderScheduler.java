package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.infrastructure.client.SettlementFailureNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// 실패(FAILED/PARTIAL_FAILED) 상태로 남은 회차의 알림 유실을 막는 백스톱
// 최초 알림은 disburse()가 회차를 확정하는 순간 한 번만 나가고 이후 disburse()는 다시 돌지 않으므로,
// 알림이 유실돼도 스스로 복구되는 경로가 없었다. 마감(COMPLETED/CLOSED_ABANDONED)되면 스캔에서 빠져 재통보가 멈춘다.
@Component
@RequiredArgsConstructor
@Slf4j
public class UnresolvedFailureReminderScheduler {

    private static final long SCAN_INTERVAL_MS = 60 * 60 * 1000L;
    private static final List<SettlementStatus> UNRESOLVED_STATUSES =
            List.of(SettlementStatus.FAILED, SettlementStatus.PARTIAL_FAILED);

    private final SettlementBatchRepository settlementBatchRepository;
    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final SettlementFailureNotifier settlementFailureNotifier;

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS)
    public void remindUnresolvedFailures() {
        // 한 종류의 조회 장애가 다른 종류의 재통보 주기를 취소하지 않도록 종류별로 조회부터 격리한다.
        runKindIsolated("배당", this::remindDividendBatches);
        runKindIsolated("최종 정산", this::remindFinalSettlementBatches);
    }

    private void remindDividendBatches() {
        List<SettlementBatch> batches = settlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED_STATUSES);
        if (batches.isEmpty()) {
            return;
        }
        log.info("미해결 실패 배당 회차 재통보 점검 ({}건)", batches.size());
        batches.forEach(batch -> runIsolated(batch.getId(), () -> settlementFailureNotifier.remindUnresolvedDividendBatch(batch, null)));
    }

    private void remindFinalSettlementBatches() {
        List<FinalSettlementBatch> batches = finalSettlementBatchRepository.findByStatusInAndIsDeletedFalse(UNRESOLVED_STATUSES);
        if (batches.isEmpty()) {
            return;
        }
        log.info("미해결 실패 최종 정산 회차 재통보 점검 ({}건)", batches.size());
        batches.forEach(batch -> runIsolated(batch.getId(), () -> settlementFailureNotifier.remindUnresolvedFinalSettlementBatch(batch)));
    }

    private void runKindIsolated(String kind, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("미해결 실패 {} 회차 재통보 점검 실패 — 다른 종류 점검은 계속 진행합니다", kind, e);
        }
    }

    // 한 회차의 실패가 다른 회차 재통보를 막지 않게 격리한다.
    private void runIsolated(Object batchId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("미해결 실패 회차 재통보 실패 (batchId={})", batchId, e);
        }
    }
}
