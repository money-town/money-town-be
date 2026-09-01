package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;

import java.util.UUID;

public record FinalSettlementRetryResponse(
        UUID finalSettlementBatchId,
        int retriedCount,
        SettlementStatus status
) {

    public static FinalSettlementRetryResponse of(FinalSettlementBatch batch, int retriedCount) {
        return new FinalSettlementRetryResponse(batch.getId(), retriedCount, batch.getStatus());
    }
}