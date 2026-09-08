package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

public record SubscriptionCompensationRequestedPayload(
        UUID offeringId,
        UUID assetId,
        String reason
) {}