package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementBatchResponse(
        UUID settlementBatchId,
        UUID assetId,
        UUID revenueId,
        LocalDate recordDate,
        Long totalAmount,
        Long carriedInAmount,
        Long remainderAmount,
        SettlementStatus status,
        int payoutCount,
        Instant createdAt
) {

    public static SettlementBatchResponse of(SettlementBatch batch, int payoutCount) {
        return new SettlementBatchResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getRevenueId(),
                batch.getRecordDate(),
                batch.getTotalAmount(),
                batch.getCarriedInAmount(),
                batch.getRemainderAmount(),
                batch.getStatus(),
                payoutCount,
                batch.getCreatedAt()
        );
    }
}