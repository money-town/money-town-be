package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementBatchDetailResponse(
        UUID settlementBatchId,
        UUID assetId,
        UUID revenueId,
        LocalDate recordDate,
        Long totalAmount,
        Long carriedInAmount,
        Long remainderAmount,
        SettlementStatus status,
        Instant createdAt,
        Instant updatedAt,
        PayoutSummary payoutSummary
) {

    public static SettlementBatchDetailResponse of(SettlementBatch batch, PayoutSummary payoutSummary) {
        return new SettlementBatchDetailResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getRevenueId(),
                batch.getRecordDate(),
                batch.getTotalAmount(),
                batch.getCarriedInAmount(),
                batch.getRemainderAmount(),
                batch.getStatus(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                payoutSummary
        );
    }

    public record PayoutSummary(
            long totalCount,
            long paidCount,
            long failedCount,
            long pendingCount
    ) {
    }
}