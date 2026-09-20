package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.ResolutionType;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class DividendPayoutWriter {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> CLAIMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);
    private static final List<PayoutStatus> IN_PROGRESS_STATUSES =
            List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING, PayoutStatus.PROCESSING);

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void markDisbursing(UUID settlementBatchId) {
        SettlementBatch batch = loadBatch(settlementBatchId);
        batch.markDisbursing();
        settlementBatchRepository.save(batch);
    }

    @Transactional
    public List<DividendPayout> claimPendingPayouts(UUID settlementBatchId) {
        List<DividendPayout> claimed = dividendPayoutRepository
                .findBySettlementBatchIdAndStatusInAndIsDeletedFalse(settlementBatchId, CLAIMABLE_STATUSES);
        claimed.forEach(DividendPayout::markProcessing);
        dividendPayoutRepository.saveAll(claimed);
        return claimed;
    }

    @Transactional
    public int reclaimStalledProcessing(Instant staleBefore) {
        List<DividendPayout> stalled = dividendPayoutRepository
                .findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, staleBefore);
        stalled.forEach(DividendPayout::revertStalledProcessing);
        dividendPayoutRepository.saveAll(stalled);
        return stalled.size();
    }

    @Transactional
    public void markPaid(UUID payoutId) {
        DividendPayout payout = loadPayout(payoutId);
        payout.markPaid();
        dividendPayoutRepository.save(payout);
    }

    @Transactional
    public void markFailedAttempt(UUID payoutId) {
        DividendPayout payout = loadPayout(payoutId);
        payout.incrementRetryCount();
        meterRegistry.counter("settlement.payout.retry", "type", "dividend").increment();
        if (payout.getRetryCount() >= MAX_RETRY_COUNT) {
            payout.markDeadLetter();
            meterRegistry.counter("settlement.payout.dead_letter", "type", "dividend", "reason", "retry_exceeded").increment();
        } else {
            payout.markRetrying();
        }
        dividendPayoutRepository.save(payout);
    }

    // 지갑 응답이 success=true인데 우리가 보낸 settlementBatchId와 다른 값을 돌려준 경우 : 즉시 DEAD_LETTER
    @Transactional
    public void markResponseMismatch(UUID payoutId) {
        DividendPayout payout = loadPayout(payoutId);
        payout.markDeadLetter();
        meterRegistry.counter("settlement.payout.dead_letter", "type", "dividend", "reason", "response_mismatch").increment();
        dividendPayoutRepository.save(payout);
    }

    @Transactional
    public Optional<SettlementBatch> updateBatchStatus(UUID settlementBatchId) {
        SettlementBatch batch = loadBatch(settlementBatchId);
        List<DividendPayout> allPayouts = dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(settlementBatchId);

        boolean anyInProgress = allPayouts.stream()
                .anyMatch(payout -> IN_PROGRESS_STATUSES.contains(payout.getStatus()));
        if (anyInProgress) {
            return Optional.empty();
        }

        boolean anyDeadLetter = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.DEAD_LETTER);
        boolean anyAbandoned = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.ABANDONED);
        boolean anyPaid = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.PAID);

        if (!anyDeadLetter && !anyAbandoned) {
            batch.markCompleted();
        } else if (anyDeadLetter) {
            // 아직 관리자가 포기 처리하지 않은 DEAD_LETTER가 남아있음 — 기존 동작 그대로 재시도(retryBatch) 대상으로 열어둠
            if (anyPaid) {
                batch.markPartialFailed();
            } else {
                batch.markFailed();
            }
        } else {
            // DEAD_LETTER는 더 이상 없고 ABANDONED만 남음 — 남은 실패 건을 관리자가 전부 포기 처리해 마감 가능해짐
            batch.markClosedAbandoned();
        }
        meterRegistry.counter("settlement.batch.status", "type", "dividend", "status", batch.getStatus().name()).increment();
        settlementBatchRepository.save(batch);
        return Optional.of(batch);
    }

    // 관리자가 명시적으로 DEAD_LETTER 건을 포기 처리한다
    // 재시도로는 해결되지 않는 건을 이 상태로 옮겨야 배치가 마감되고 자산의 다음 회차가 열릴 수 있다.
    @Transactional
    public DividendPayout abandonPayout(UUID payoutId, ResolutionType resolutionType,
                                         String resolutionReference, String resolutionNote) {
        DividendPayout payout = loadPayout(payoutId);
        if (payout.getStatus() != PayoutStatus.DEAD_LETTER) {
            throw new BusinessException(SettlementErrorCode.PAYOUT_NOT_ABANDONABLE);
        }
        payout.abandon(resolutionType, resolutionReference, resolutionNote);
        dividendPayoutRepository.save(payout);
        return payout;
    }

    private SettlementBatch loadBatch(UUID settlementBatchId) {
        return settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));
    }

    private DividendPayout loadPayout(UUID payoutId) {
        return dividendPayoutRepository.findByIdAndIsDeletedFalse(payoutId)
                .orElseThrow(() -> new IllegalStateException("지급 건을 찾을 수 없습니다: " + payoutId));
    }
}