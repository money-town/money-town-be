package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

public record SubscriptionRequestedPayload(
        UUID offeringId,
        Long quantity,
        String idempotencyKey,
        String requestHash
) {
}
