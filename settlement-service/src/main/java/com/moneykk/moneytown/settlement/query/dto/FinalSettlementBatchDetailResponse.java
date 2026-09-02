package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;

import java.time.Instant;
import java.util.UUID;

public record FinalSettlementBatchDetailResponse(
        UUID finalSettlementBatchId,
        UUID assetId,
        Instant terminatedAt,
        Long unitPrice,
        Long totalAmount,
        SettlementStatus status,
        Progress progress,
        Instant createdAt,
        Instant updatedAt
) {

    public static FinalSettlementBatchDetailResponse of(FinalSettlementBatch batch, Progress progress) {
        return new FinalSettlementBatchDetailResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getTerminatedAt(),
                batch.getUnitPrice(),
                batch.getTotalAmount(),
                batch.getStatus(),
                progress,
                batch.getCreatedAt(),
                batch.getUpdatedAt()
        );
    }

    public record Progress(
            long totalCount,
            long paidCount,
            long failedCount,
            long pendingCount
    ) {
    }
}