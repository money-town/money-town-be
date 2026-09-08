package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryRequest;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetHoldingsSnapshotFetcher;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalSettlementCommandService {

    private static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Seoul");
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final String ASSET_ID_UNIQUE_CONSTRAINT = "uk_final_settlement_batches_asset_id";

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    private final FinalSettlementPayoutWriter finalSettlementPayoutWriter;
    private final AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;

    @Transactional
    public FinalSettlementBatchResponse openFinalSettlement(String role, OpenFinalSettlementRequest request) {
        validateSystem(role);
        Optional<FinalSettlementBatch> existingBatch =
                finalSettlementBatchRepository.findByAssetIdAndIsDeletedFalse(request.assetId());
        if (existingBatch.isPresent()) {
            return FinalSettlementBatchResponse.of(existingBatch.get(), false);
        }

        LocalDate asOf = request.terminatedAt().atZone(SETTLEMENT_ZONE).toLocalDate();
        List<HoldingItem> holders = fetchHolders(request.assetId(), asOf);
        if (holders.isEmpty()) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_HOLDERS_NOT_FOUND);
        }

        long totalAmount = holders.stream()
                .mapToLong(holder -> holder.quantity() * request.unitPrice())
                .sum();

        FinalSettlementBatch batch = FinalSettlementBatch.open(
                request.assetId(), request.terminatedAt(), request.unitPrice(), totalAmount);
        batch.markCalculated();

        List<FinalSettlementPayout> payouts = holders.stream()
                .map(holder -> FinalSettlementPayout.queue(
                        batch.getId(), holder.userId(), holder.quantity(), holder.quantity() * request.unitPrice()))
                .toList();

        return saveNewBatchOrReturnExisting(request.assetId(), batch, payouts);
    }

    // 앞선 findByAssetIdAndIsDeletedFalse 조회를 동시 요청 두 건이 함께 통과하면(둘 다 아직 커밋 전),
    // 유니크 제약(uk_final_settlement_batches_asset_id)이 DB 레벨에서 하나만 통과시킨다.
    // saveNewBatch는 REQUIRES_NEW로 별도 트랜잭션에서 실행되므로, 위반 시 그 트랜잭션만 롤백되고
    // 여기서 새 트랜잭션으로 먼저 생성된 배치를 조회해 멱등 응답(newlyCreated=false)으로 돌려줄 수 있다.
    private FinalSettlementBatchResponse saveNewBatchOrReturnExisting(
            UUID assetId, FinalSettlementBatch batch, List<FinalSettlementPayout> payouts) {
        try {
            finalSettlementPayoutWriter.saveNewBatch(batch, payouts);
        } catch (DataIntegrityViolationException e) {
            if (!ASSET_ID_UNIQUE_CONSTRAINT.equals(extractConstraintName(e))) {
                throw e;
            }
            FinalSettlementBatch winnerBatch = finalSettlementBatchRepository.findByAssetIdAndIsDeletedFalse(assetId)
                    .orElseThrow(() -> new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));
            return FinalSettlementBatchResponse.of(winnerBatch, false);
        }
        return FinalSettlementBatchResponse.of(batch, true);
    }

    private String extractConstraintName(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException constraintViolation
                ? constraintViolation.getConstraintName()
                : null;
    }

    @Transactional
    public FinalSettlementRetryResponse retryFinalSettlement(String role, UUID finalSettlementBatchId, FinalSettlementRetryRequest request) {
        validateAdmin(role);
        FinalSettlementBatch batch = finalSettlementBatchRepository.findByIdAndIsDeletedFalse(finalSettlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));

        if (!isRetryable(batch.getStatus())) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_RETRYABLE);
        }

        List<FinalSettlementPayout> retryablePayouts = findRetryablePayouts(finalSettlementBatchId, request);
        if (retryablePayouts.isEmpty()) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_NO_RETRYABLE_PAYOUTS);
        }

        retryablePayouts.forEach(FinalSettlementPayout::requeue);
        batch.markDisbursing();

        finalSettlementBatchRepository.save(batch);
        finalSettlementPayoutRepository.saveAll(retryablePayouts);

        return FinalSettlementRetryResponse.of(batch, retryablePayouts.size());
    }

    private boolean isRetryable(SettlementStatus status) {
        return status == SettlementStatus.FAILED || status == SettlementStatus.PARTIAL_FAILED;
    }

    private void validateAdmin(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_ACCESS_DENIED);
        }
    }

    private void validateSystem(String role) {
        if (!SYSTEM_ROLE.equals(role)) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_SYSTEM_ACCESS_DENIED);
        }
    }

    private List<FinalSettlementPayout> findRetryablePayouts(UUID finalSettlementBatchId, FinalSettlementRetryRequest request) {
        List<UUID> payoutIds = request.finalSettlementPayoutIds();
        if (payoutIds == null || payoutIds.isEmpty()) {
            return finalSettlementPayoutRepository
                    .findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(finalSettlementBatchId, PayoutStatus.DEAD_LETTER);
        }
        return finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndIdInAndStatusAndIsDeletedFalse(finalSettlementBatchId, payoutIds, PayoutStatus.DEAD_LETTER);
    }

    private List<HoldingItem> fetchHolders(UUID assetId, LocalDate asOf) {
        AssetHoldingsSnapshotFetcher.Aggregated aggregated = assetHoldingsSnapshotFetcher.fetchAll(assetId, asOf);

        return aggregated.items().stream()
                .filter(item -> item.quantity() != null && item.quantity() > 0)
                .toList();
    }
}