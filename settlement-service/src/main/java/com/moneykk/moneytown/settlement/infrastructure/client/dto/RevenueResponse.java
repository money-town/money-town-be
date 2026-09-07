package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RevenueResponse(
        UUID revenueId,
        UUID assetId,
        String revenueType,
        String sourceType,
        String sourceReferenceId,
        BigDecimal grossAmount,
        BigDecimal expenseAmount,
        BigDecimal feeAmount,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        RevenueTransferStatus transferStatus
) {
}