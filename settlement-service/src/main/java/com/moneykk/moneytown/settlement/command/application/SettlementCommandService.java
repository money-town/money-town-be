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
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.service.DividendDistributionCalculator;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetHoldingsSnapshotFetcher;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementCommandService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;
    private final AssetServiceClient assetServiceClient;
    private final AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;
    private final SettlementBatchWriter settlementBatchWriter;

    // 수익 폴링 스케줄러가 자동으로 개시할 때 사용 — 사람의 요청이 아니므로 ADMIN 검사X
    // 배당 기준일을 직접 지정할 ADMIN 입력도 없으므로 항상 periodEnd로 대체
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SettlementBatchResponse openBatchAutomatically(UUID assetId, UUID revenueId) {
        return openBatchInternal(assetId, revenueId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SettlementBatchResponse openBatch(String role, UUID assetId, UUID revenueId, LocalDate recordDateOverride) {
        validateAdmin(role);
        try {
            return openBatchInternal(assetId, revenueId, recordDateOverride);
        } catch (BusinessException e) {
            log.warn("정산 회차 개시 실패 (assetId={}, revenueId={}, reason={})", assetId, revenueId, e.getErrorCode());
            throw e;
        }
    }

    private SettlementBatchResponse openBatchInternal(UUID assetId, UUID revenueId, LocalDate recordDateOverride) {
        // 전환 기간(폴링+Kafka 이중 트리거)·Kafka at-least-once 재전송 양쪽 다
        // 같은 revenueId로 이 메서드가 반복 호출될 수 있다 — 기존 배치가 있으면 예외 대신 그대로 반환
        Optional<SettlementBatch> existingByRevenue = settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(revenueId);
        if (existingByRevenue.isPresent()) {
            return existingBatchResponse(existingByRevenue.get(), assetId);
        }

        guardAgainstConcurrentBatchForAsset(assetId);

        RevenueResponse revenue = fetchAndValidateRevenue(assetId, revenueId);
        // 배당 기준일은 정산 회차가 자체적으로 관리하는 값(ADMIN이 명시하면 그 값을 쓰고, 미지정 시에만 수익 발생 기간 종료일로 대체)
        LocalDate recordDate = recordDateOverride != null ? recordDateOverride : revenue.periodEnd();

        long totalAmount = calculateDistributableAmount(revenue);
        if (totalAmount <= 0) {
            throw new BusinessException(SettlementErrorCode.DISTRIBUTABLE_AMOUNT_NOT_POSITIVE);
        }

        AssetHoldingsSnapshotFetcher.Aggregated holdingsSnapshot = fetchAndValidateHoldingsSnapshot(assetId, recordDate);

        SettlementBatch batch = SettlementBatch.open(assetId, revenueId, recordDate, totalAmount);
        batch.markSnapshotTaken();

        HoldingSnapshot snapshot = captureHoldingSnapshot(batch, holdingsSnapshot);

        DividendDistributionCalculator.Distribution distribution = DividendDistributionCalculator.distribute(
                totalAmount, holdingsSnapshot.totalHoldingQuantity(), holdingsSnapshot.items());
        batch.markCalculated();

        List<DividendPayout> payouts = distribution.allocations().stream()
                .map(allocation -> DividendPayout.queue(batch.getId(), allocation.investorId(), allocation.shareRatio(), allocation.amount()))
                .toList();

        log.info("[진단]holdings 페이징 완료, persist 호출 시작 (assetId={}, revenueId={})", assetId, revenueId);
        try {
            settlementBatchWriter.persist(batch, snapshot, payouts);
        } catch (BusinessException e) {
            if (e.getErrorCode() != SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE) {
                throw e;
            }
            // 위 findByRevenueIdAndIsDeletedFalse 조회를 폴링과 Kafka Consumer가 동시에 통과하면 둘 다 insert 시도
            // 진 쪽은 uk_settlement_batches_revenue_id 위반으로 여기서 잡힘
            // 이긴 쪽이 만든 배치를 새로 조회해 돌려준다.
            SettlementBatch winnerBatch = settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(revenueId)
                    .orElseThrow(() -> e);
            log.info("[진단]persist 경합 발생 — 기존 배치로 대체 반환 (loserBatchId={}, winnerBatchId={})",
                    batch.getId(), winnerBatch.getId());
            return existingBatchResponse(winnerBatch, assetId);
        }
        log.info("[진단]persist 완료 — 저장 종료 (batchId={}, payoutCount={})", batch.getId(), payouts.size());

        log.info("정산 회차 개시 완료 (assetId={}, revenueId={}, settlementBatchId={}, recordDate={}, totalAmount={}, payoutCount={})",
                assetId, revenueId, batch.getId(), recordDate, totalAmount, payouts.size());
        return SettlementBatchResponse.of(batch, payouts.size(), true);
    }

    private SettlementBatchResponse existingBatchResponse(SettlementBatch batch, UUID assetId) {
        if (!batch.getAssetId().equals(assetId)) {
            throw new BusinessException(SettlementErrorCode.REVENUE_ASSET_MISMATCH);
        }
        log.info("정산 회차 개시 멱등 재조회 (assetId={}, revenueId={}, settlementBatchId={})",
                batch.getAssetId(), batch.getRevenueId(), batch.getId());
        long payoutCount = dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(batch.getId());
        return SettlementBatchResponse.of(batch, (int) payoutCount, false);
    }

    @Transactional
    public SettlementBatchResponse retryBatch(String role, UUID settlementBatchId) {
        validateAdmin(role);
        try {
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

            log.info("정산 회차 재시도 개시 (settlementBatchId={}, 재처리 건수={})", settlementBatchId, deadLetterPayouts.size());
            return SettlementBatchResponse.of(batch, deadLetterPayouts.size(), false);
        } catch (BusinessException e) {
            log.warn("정산 회차 재시도 실패 (settlementBatchId={}, reason={})", settlementBatchId, e.getErrorCode());
            throw e;
        }
    }

    private boolean isRetryable(SettlementStatus status) {
        return status == SettlementStatus.FAILED || status == SettlementStatus.PARTIAL_FAILED;
    }

    private void validateAdmin(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED);
        }
    }

    private void guardAgainstConcurrentBatchForAsset(UUID assetId) {
        if (settlementBatchRepository.existsByAssetIdAndStatusNotAndIsDeletedFalse(assetId, SettlementStatus.COMPLETED)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);
        }
    }

    private RevenueResponse fetchAndValidateRevenue(UUID assetId, UUID revenueId) {
        RevenueResponse revenue = FeignExceptionTranslator.call(
                () -> assetServiceClient.getRevenue(assetId, revenueId, "SYSTEM").data(),
                SettlementErrorCode.ASSET_REVENUE_NOT_FOUND);

        if (!assetId.equals(revenue.assetId())) {
            throw new BusinessException(SettlementErrorCode.REVENUE_ASSET_MISMATCH);
        }
        if (isInvalidAmount(revenue)) {
            throw new BusinessException(SettlementErrorCode.REVENUE_AMOUNT_INVALID);
        }
        if (revenue.transferStatus() != RevenueTransferStatus.READY) {
            throw new BusinessException(SettlementErrorCode.REVENUE_NOT_READY);
        }
        return revenue;
    }

    private boolean isInvalidAmount(RevenueResponse revenue) {
        return revenue.grossAmount() == null || revenue.grossAmount().signum() <= 0
                || revenue.expenseAmount() == null || revenue.expenseAmount().signum() < 0
                || revenue.feeAmount() == null || revenue.feeAmount().signum() < 0;
    }

    private long calculateDistributableAmount(RevenueResponse revenue) {
        BigDecimal distributable = revenue.grossAmount()
                .subtract(revenue.expenseAmount())
                .subtract(revenue.feeAmount());
        return distributable.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    private AssetHoldingsSnapshotFetcher.Aggregated fetchAndValidateHoldingsSnapshot(UUID assetId, LocalDate recordDate) {
        AssetHoldingsSnapshotFetcher.Aggregated aggregated = assetHoldingsSnapshotFetcher.fetchAll(assetId, recordDate);

        Long totalHoldingQuantity = aggregated.totalHoldingQuantity();
        if (totalHoldingQuantity == null || totalHoldingQuantity <= 0) {
            throw new BusinessException(SettlementErrorCode.HOLDING_SNAPSHOT_INVALID);
        }

        return aggregated;
    }

    private HoldingSnapshot captureHoldingSnapshot(SettlementBatch batch, AssetHoldingsSnapshotFetcher.Aggregated holdingsSnapshot) {
        int totalHolders = (int) holdingsSnapshot.items().stream()
                .filter(holding -> holding.quantity() != null && holding.quantity() > 0)
                .count();

        return HoldingSnapshot.capture(batch.getId(), batch.getAssetId(), batch.getRecordDate(),
                holdingsSnapshot.totalHoldingQuantity(), totalHolders, holdingsSnapshot.totalHoldingQuantity());
    }
}