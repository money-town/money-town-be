package com.moneykk.moneytown.analysis.ai.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

public record OfferingSummary(
        UUID offeringId,
        UUID assetId,
        String title,
        Long pricePerUnit,
        Long totalQuantity,
        Long remainingQuantity,
        Instant endAt) {
}
