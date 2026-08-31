package com.moneykk.moneytown.offering.offering.command.dto.request;

import java.time.Instant;

public record OfferingUpdateRequest(
        String title,
        Long totalQuantity,
        Long minSubscriptionQuantity,
        Long maxSubscriptionQuantity,
        Instant startAt,
        Instant endAt
) {
}