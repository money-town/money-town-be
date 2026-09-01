package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;

import java.util.UUID;

public record FinalSettlementBatchResponse(
        UUID finalSettlementBatchId,
        UUID assetId,
        Long totalAmount,
        SettlementStatus status
) {

    public static FinalSettlementBatchResponse of(FinalSettlementBatch batch) {
        return new FinalSettlementBatchResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getTotalAmount(),
                batch.getStatus()
        );
    }
}