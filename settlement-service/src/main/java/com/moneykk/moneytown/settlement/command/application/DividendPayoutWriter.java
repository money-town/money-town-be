package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.ResolutionType;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.DividendPayoutDispatchPayload;
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
    private final OutboxEventStore outboxEventStore;
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
        // PROCESSING 전환과 "지급 요청 메시지가 나갈 것"을 같은 트랜잭션으로 묶는다(Outbox).
        // key = payoutId(aggregateId)라 한 회차의 payout이 파티션 전체에 분산된다.
        claimed.forEach(payout -> outboxEventStore.save(
                DividendPayoutDispatchPayload.AGGREGATE_TYPE,
                DividendPayoutDispatchPayload.TOPIC,
                EventEnvelope.of(
                        DividendPayoutDispatchPayload.EVENT_TYPE,
                        payout.getId().toString(),
                        null,
                        settlementBatchId.toString(),
                        new DividendPayoutDispatchPayload(
                                payout.getId(), settlementBatchId, payout.getInvestorId(), payout.getAmount()))));
        return claimed;
    }

    // 컨슈머가 같은 메시지를 다시 받아도(at-least-once) 이미 PROCESSING이 아닌 건은 지갑을 다시 부르지 않게 하는 멱등 가드
    @Transactional(readOnly = true)
    public boolean isProcessing(UUID payoutId) {
        return dividendPayoutRepository.findByIdAndIsDeletedFalse(payoutId)
                .map(payout -> payout.getStatus() == PayoutStatus.PROCESSING)
                .orElse(false);
    }

    // 회차 마감 판정의 싼 1차 확인 — 진행 중인 건이 하나라도 있으면 전체 payout 로드(updateBatchStatus)를 하지 않는다
    @Transactional(readOnly = true)
    public boolean hasInProgressPayouts(UUID settlementBatchId) {
        return dividendPayoutRepository.existsBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                settlementBatchId, IN_PROGRESS_STATUSES);
    }

    // 마지막 건을 동시에 끝낸 컨슈머들 중 한 곳만 마감 판정을 하도록 회차 행을 잠그고 DISBURSING인지 다시 확인한다
    @Transactional
    public Optional<SettlementBatch> finalizeBatchIfDisbursing(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findWithLockByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));
        if (batch.getStatus() != SettlementStatus.DISBURSING) {
            return Optional.empty();
        }
        return updateBatchStatus(settlementBatchId);
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