package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.client.FeignExceptionTranslator;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.HoldingSnapshotRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.service.DividendDistributionCalculator;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetHoldingsSnapshotFetcher;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementCommandService {

    private static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Seoul");

    private final SettlementBatchRepository settlementBatchRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;
    private final DividendPayoutRepository dividendPayoutRepository;
    private final AssetServiceClient assetServiceClient;
    private final AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;

    @Transactional
    public SettlementBatchResponse openBatch(UUID assetId, UUID revenueId) {
        guardAgainstDuplicateOrConcurrentBatch(assetId, revenueId);

        RevenueResponse revenue = fetchAndValidateRevenue(assetId, revenueId);
        LocalDate recordDate = revenue.recordDate();

        long distributableAmount = calculateDistributableAmount(revenue);
        long carriedInAmount = findCarriedInAmount(assetId);
        long totalAmount = distributableAmount + carriedInAmount;
        if (totalAmount <= 0) {
            throw new BusinessException(SettlementErrorCode.DISTRIBUTABLE_AMOUNT_NOT_POSITIVE);
        }

        HoldingsSnapshotResponse holdingsSnapshot = fetchAndValidateHoldingsSnapshot(assetId, recordDate);

        SettlementBatch batch = SettlementBatch.open(assetId, revenueId, recordDate, distributableAmount, carriedInAmount);
        batch.markSnapshotTaken();

        HoldingSnapshot snapshot = captureHoldingSnapshot(batch, holdingsSnapshot);

        DividendDistributionCalculator.Distribution distribution = DividendDistributionCalculator.distribute(
                totalAmount, holdingsSnapshot.totalHoldingQuantity(), holdingsSnapshot.items());
        batch.markCalculated(distribution.remainderAmount());

        List<DividendPayout> payouts = distribution.allocations().stream()
                .map(allocation -> DividendPayout.queue(batch.getId(), allocation.investorId(), allocation.shareRatio(), allocation.amount()))
                .toList();

        settlementBatchRepository.save(batch);
        holdingSnapshotRepository.save(snapshot);
        dividendPayoutRepository.saveAll(payouts);

        return SettlementBatchResponse.of(batch, payouts.size());
    }

    @Transactional
    public SettlementBatchResponse retryBatch(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        if (!isRetryable(batch.getStatus())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_RETRYABLE);
        }

        List<DividendPayout> deadLetterPayouts = dividendPayoutRepository
                .findBySettlementBatchIdAndStatusAndIsDeletedFalse(settlementBatchId, PayoutStatus.DEAD_LETTER);
        deadLetterPayouts.forEach(DividendPayout::requeue);

        batch.markDisbursing();

        settlementBatchRepository.save(batch);
        dividendPayoutRepository.saveAll(deadLetterPayouts);

        return SettlementBatchResponse.of(batch, deadLetterPayouts.size());
    }

    private boolean isRetryable(SettlementStatus status) {
        return status == SettlementStatus.FAILED || status == SettlementStatus.PARTIAL_FAILED;
    }

    private void guardAgainstDuplicateOrConcurrentBatch(UUID assetId, UUID revenueId) {
        if (settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(revenueId)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE);
        }
        if (settlementBatchRepository.existsByAssetIdAndStatusNotAndIsDeletedFalse(assetId, SettlementStatus.COMPLETED)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);
        }
    }

    private RevenueResponse fetchAndValidateRevenue(UUID assetId, UUID revenueId) {
        RevenueResponse revenue = FeignExceptionTranslator.call(
                () -> assetServiceClient.getRevenue(assetId, revenueId).data(),
                SettlementErrorCode.ASSET_REVENUE_NOT_FOUND);

        if (!assetId.equals(revenue.assetId())) {
            throw new BusinessException(SettlementErrorCode.REVENUE_ASSET_MISMATCH);
        }
        if (isInvalidAmount(revenue)) {
            throw new BusinessException(SettlementErrorCode.REVENUE_AMOUNT_INVALID);
        }
        if (isRecordDateBeforeOccurrence(revenue)) {
            throw new BusinessException(SettlementErrorCode.REVENUE_PERIOD_INVALID);
        }
        if (revenue.transferStatus() != RevenueTransferStatus.PENDING) {
            throw new BusinessException(SettlementErrorCode.REVENUE_NOT_READY);
        }
        return revenue;
    }

    private boolean isInvalidAmount(RevenueResponse revenue) {
        return revenue.grossAmount() == null || revenue.grossAmount().signum() <= 0
                || revenue.expenseAmount() == null || revenue.expenseAmount().signum() < 0
                || revenue.feeAmount() == null || revenue.feeAmount().signum() < 0;
    }

    // 배당 기준일(recordDate)이 수익 발생 시각(occurredAt)보다 앞설 수는 없다.
    private boolean isRecordDateBeforeOccurrence(RevenueResponse revenue) {
        if (revenue.recordDate() == null || revenue.occurredAt() == null) {
            return true;
        }
        LocalDate occurredDate = revenue.occurredAt().atZone(SETTLEMENT_ZONE).toLocalDate();
        return revenue.recordDate().isBefore(occurredDate);
    }

    private long calculateDistributableAmount(RevenueResponse revenue) {
        BigDecimal distributable = revenue.grossAmount()
                .subtract(revenue.expenseAmount())
                .subtract(revenue.feeAmount());
        return distributable.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    private long findCarriedInAmount(UUID assetId) {
        Optional<SettlementBatch> previousCompletedBatch =
                settlementBatchRepository.findFirstByAssetIdAndStatusAndIsDeletedFalseOrderByRecordDateDesc(assetId, SettlementStatus.COMPLETED);
        return previousCompletedBatch.map(SettlementBatch::getRemainderAmount).orElse(0L);
    }

    private HoldingsSnapshotResponse fetchAndValidateHoldingsSnapshot(UUID assetId, LocalDate recordDate) {
        AssetHoldingsSnapshotFetcher.Aggregated aggregated = assetHoldingsSnapshotFetcher.fetchAll(assetId, recordDate);

        Long totalHoldingQuantity = aggregated.totalHoldingQuantity();
        if (totalHoldingQuantity == null || totalHoldingQuantity <= 0) {
            throw new BusinessException(SettlementErrorCode.HOLDING_SNAPSHOT_INVALID);
        }

        return new HoldingsSnapshotResponse(assetId, recordDate, totalHoldingQuantity, aggregated.items(), null, false);
    }

    private HoldingSnapshot captureHoldingSnapshot(SettlementBatch batch, HoldingsSnapshotResponse holdingsSnapshot) {
        int totalHolders = (int) holdingsSnapshot.items().stream()
                .filter(holding -> holding.quantity() != null && holding.quantity() > 0)
                .count();

        return HoldingSnapshot.capture(batch.getId(), batch.getAssetId(), batch.getRecordDate(),
                holdingsSnapshot.totalHoldingQuantity(), totalHolders, holdingsSnapshot.totalHoldingQuantity());
    }
}