package com.moneykk.moneytown.settlement.query.dto;

import java.util.UUID;

public record SettlementReconciliationResponse(
        UUID settlementBatchId,
        long expectedAmount,
        long totalPayoutAmount,
        long paidAmount,
        boolean reconciled
) {

    public static SettlementReconciliationResponse of(
            UUID settlementBatchId, long expectedAmount, long totalPayoutAmount, long paidAmount) {
        return new SettlementReconciliationResponse(
                settlementBatchId, expectedAmount, totalPayoutAmount, paidAmount, totalPayoutAmount == expectedAmount);
    }
}