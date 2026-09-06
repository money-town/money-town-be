package com.moneykk.moneytown.settlement.query.dto;

import java.util.UUID;

public record FinalSettlementReconciliationResponse(
        UUID finalSettlementBatchId,
        long expectedAmount,
        long totalPayoutAmount,
        long paidAmount,
        boolean reconciled
) {

    public static FinalSettlementReconciliationResponse of(
            UUID finalSettlementBatchId, long expectedAmount, long totalPayoutAmount, long paidAmount) {
        return new FinalSettlementReconciliationResponse(
                finalSettlementBatchId, expectedAmount, totalPayoutAmount, paidAmount, totalPayoutAmount == expectedAmount);
    }
}