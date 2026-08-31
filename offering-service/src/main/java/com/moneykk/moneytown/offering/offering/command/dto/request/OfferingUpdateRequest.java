package com.moneykk.moneytown.offering.offering.command.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

public record OfferingUpdateRequest(
        String title,
        BigDecimal pricePerUnit,
        Long totalQuantity,
        Long minSubscriptionQuantity,
        Long maxSubscriptionQuantity,
        Instant startAt,
        Instant endAt
) {
}