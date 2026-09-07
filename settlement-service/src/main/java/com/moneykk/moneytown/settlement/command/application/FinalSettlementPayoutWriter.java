package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
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

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;

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
        if (payout.getRetryCount() >= MAX_RETRY_COUNT) {
            payout.markDeadLetter();
        } else {
            payout.markRetrying();
        }
        finalSettlementPayoutRepository.save(payout);
    }

    // 지갑 응답이 success=true인데 우리가 보낸 finalSettlementBatchId와 다른 값을 돌려준 경우 : 즉시 DEAD_LETTER
    @Transactional
    public void markResponseMismatch(UUID payoutId) {
        FinalSettlementPayout payout = loadPayout(payoutId);
        payout.markDeadLetter();
        finalSettlementPayoutRepository.save(payout);
    }

    @Transactional
    public Optional<UUID> updateBatchStatus(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        List<FinalSettlementPayout> allPayouts =
                finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(finalSettlementBatchId);

        boolean anyInProgress = allPayouts.stream()
                .anyMatch(payout -> IN_PROGRESS_STATUSES.contains(payout.getStatus()));
        if (anyInProgress) {
            return Optional.empty();
        }

        boolean anyDeadLetter = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.DEAD_LETTER);
        boolean anyPaid = allPayouts.stream().anyMatch(payout -> payout.getStatus() == PayoutStatus.PAID);

        if (!anyDeadLetter) {
            batch.markCompleted();
        } else if (anyPaid) {
            batch.markPartialFailed();
        } else {
            batch.markFailed();
        }
        finalSettlementBatchRepository.save(batch);

        return batch.getStatus() == SettlementStatus.COMPLETED
                ? Optional.of(batch.getAssetId())
                : Optional.empty();
    }

    @Transactional
    public void markAssetTerminationCompleted(UUID finalSettlementBatchId, Instant completedAt) {
        FinalSettlementBatch batch = loadBatch(finalSettlementBatchId);
        batch.markAssetTerminationCompleted(completedAt);
        finalSettlementBatchRepository.save(batch);
    }

    // COMPLETED인데 자산 서비스 종료 완료 통보에 아직 성공하지 못한(asset_termination_completed_at이 NULL인) 회차를 찾는다.
    @Transactional(readOnly = true)
    public List<FinalSettlementBatch> findCompletedBatchesPendingTerminationNotification() {
        return finalSettlementBatchRepository
                .findByStatusAndAssetTerminationCompletedAtIsNullAndIsDeletedFalse(SettlementStatus.COMPLETED);
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
