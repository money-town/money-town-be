package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

public record SubscriptionConfirmedPayload(
        UUID offeringId,
        UUID assetId,
        Long quantity
) {
}