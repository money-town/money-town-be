package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DeadLetterReason;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.ResolutionType;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FinalSettlementPayoutWriter {

    private static final int MAX_RETRY_COUNT = 3;
    private static final List<PayoutStatus> CLAIMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);
    private static final List<PayoutStatus> IN_PROGRESS_STATUSES =
            List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING, PayoutStatus.PROCESSING);
    private static final List<SettlementStatus> DISBURSABLE_STATUSES =
            List.of(SettlementStatus.CALCULATED, SettlementStatus.DISBURSING);
    // COMPLETED 또는 CLOSED_ABANDONED 둘 다 "정산 시스템 관점에서는 종결됐다"는 뜻
    // 자산 종료 완료 통보 대상에 포함
    private static final List<SettlementStatus> TERMINATION_NOTIFY_ELIGIBLE_STATUSES =
            List.of(SettlementStatus.COMPLETED, SettlementStatus.CLOSED_ABANDONED);

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    private final MeterRegistry meterRegistry;

    // 별도 트랜잭션(REQUIRES_NEW)에서 실행한다 — asset_id 유니크 제약 위반이 나도 이 트랜잭션만 롤백되고,
    // 호출자(FinalSettlementCommandService)가 이어서 기존 배치를 새 트랜잭션으로 조회할 수 있어야 하기 때문이다.
    // (Postgres는 제약 위반이 나면 그 커넥션의 트랜잭션 전체가 실패 상태가 되어, 같은 트랜잭션에서는 추가 조회가 불가능하다.)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNewBatch(FinalSettlementBatch batch, List<FinalSettlementPayout> payouts) {
        finalSettlementBatchRepository.saveAndFlush(batch);
        finalSettlementPayoutRepository.saveAll(payouts);
    }

    // 이미 COMPLETED/PARTIAL_FAILED/FAILED로 확정된 배치는 중복 호출(멱등 재시도, 동시 요청 등)이 들어와도 되돌리지 않는다.
    // DISBURSING은 retryFinalSettlement가 이미 전환해 둔 상태를 그대로 유지하기 위해 허용한다(재처리 흐름과의 정합).
    @Transactional
    public void markDisbursing(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        if (!DISBURSABLE_STATUSES.contains(batch.getStatus())) {
            return;
        }
        batch.markDisbursing();
        finalSettlementBatchRepository.save(batch);
    }

    @Transactional
    public List<FinalSettlementPayout> claimPendingPayouts(UUID finalSettlementBatchId) {
        List<FinalSettlementPayout> claimed = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(finalSettlementBatchId, CLAIMABLE_STATUSES);
        claimed.forEach(FinalSettlementPayout::markProcessing);
        finalSettlementPayoutRepository.saveAll(claimed);
        return claimed;
    }

    @Transactional
    public int reclaimStalledProcessing(Instant staleBefore) {
        List<FinalSettlementPayout> stalled = finalSettlementPayoutRepository
                .findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, staleBefore);
        stalled.forEach(FinalSettlementPayout::revertStalledProcessing);
        finalSettlementPayoutRepository.saveAll(stalled);
        return stalled.size();
    }

    @Transactional
    public void markPaid(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.markPaid();
        finalSettlementPayoutRepository.save(payout);
    }

    @Transactional
    public void markFailedAttempt(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.incrementRetryCount();
        meterRegistry.counter("settlement.payout.retry", "type", "final").increment();
        if (payout.getRetryCount() >= MAX_RETRY_COUNT) {
            payout.markDeadLetter(DeadLetterReason.RETRY_EXCEEDED);
            meterRegistry.counter("settlement.payout.dead_letter", "type", "final", "reason", "retry_exceeded").increment();
        } else {
            payout.markRetrying();
        }
        finalSettlementPayoutRepository.save(payout);
    }

    // 지갑 응답이 success=true인데 우리가 보낸 finalSettlementBatchId와 다른 값을 돌려준 경우 : 즉시 DEAD_LETTER
    // RESPONSE_MISMATCH로 표시해 재처리 API 대상에서 제외한다 — 지갑 트랜잭션 대조 후 관리자가 수동 지급(abandon)해야 한다
    @Transactional
    public void markResponseMismatch(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.markDeadLetter(DeadLetterReason.RESPONSE_MISMATCH);
        meterRegistry.counter("settlement.payout.dead_letter", "type", "final", "reason", "response_mismatch").increment();
        finalSettlementPayoutRepository.save(payout);
    }

    @Transactional
    public Optional<FinalSettlementBatch> updateBatchStatus(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        List<FinalSettlementPayout> allPayouts =
                finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(finalSettlementBatchId);

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
            // 아직 관리자가 포기 처리하지 않은 DEAD_LETTER가 남아있음 — 기존 동작 그대로 재시도 대상으로 열어둠
            if (anyPaid) {
                batch.markPartialFailed();
            } else {
                batch.markFailed();
            }
        } else {
            // DEAD_LETTER는 더 이상 없고 ABANDONED만 남음 — 관리자가 전부 포기 처리해 마감 가능해짐
            // 자산 종료 완료 통보는 COMPLETED, CLOSED_ABANDONED 대상에 포함 -> DisbursementRetryScheduler의 백스톱 다음 사이클에 자동으로 통보
            batch.markClosedAbandoned();
        }
        meterRegistry.counter("settlement.batch.status", "type", "final", "status", batch.getStatus().name()).increment();
        finalSettlementBatchRepository.save(batch);

        return Optional.of(batch);
    }

    // 관리자가 명시적으로 DEAD_LETTER 건을 포기 처리
    // 관리자가 이미 다른 방법으로 실제 지급(원금 반환)을 완료한 뒤에만 호출
    @Transactional
    public FinalSettlementPayout abandonPayout(UUID payoutId, ResolutionType resolutionType,
                                                String resolutionReference, String resolutionNote) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        if (payout.getStatus() != PayoutStatus.DEAD_LETTER) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_PAYOUT_NOT_ABANDONABLE);
        }
        payout.abandon(resolutionType, resolutionReference, resolutionNote);
        finalSettlementPayoutRepository.save(payout);
        return payout;
    }

    @Transactional
    public void markAssetTerminationCompleted(UUID finalSettlementBatchId, Instant completedAt) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        batch.markAssetTerminationCompleted(completedAt);
        finalSettlementBatchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public List<FinalSettlementBatch> findCompletedBatchesPendingTerminationNotification() {
        return finalSettlementBatchRepository
                .findByStatusInAndAssetTerminationCompletedAtIsNullAndIsDeletedFalse(TERMINATION_NOTIFY_ELIGIBLE_STATUSES);
    }

    private FinalSettlementBatch loadBatch(UUID finalSettlementBatchId) {
        return finalSettlementBatchRepository.findByIdAndIsDeletedFalse(finalSettlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));
    }

    private FinalSettlementPayout loadPayout(UUID payoutId) {
        return finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payoutId)
                .orElseThrow(() -> new IllegalStateException("지급 건을 찾을 수 없습니다: " + payoutId));
    }
}
