package com.moneykk.moneytown.analysis.ai.command.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// client 응답 병합용
public record PortfolioCandidate(
        UUID offeringId,
        String title,
        long pricePerUnit,
        long totalQuantity,
        long remainingQuantity,
        int subscriptionRatePercent,
        long totalRaiseAmount,
        long daysToClose,
        Instant endAt,
        String assetType,              // asset 누락 시 null
        BigDecimal expectedReturnRate, // null 허용
        Long valuationAmount,          // null 허용
        String description             // null 허용
) {
}
