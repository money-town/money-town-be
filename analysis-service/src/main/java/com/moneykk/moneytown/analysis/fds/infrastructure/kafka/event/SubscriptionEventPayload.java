package com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event;

import java.util.UUID;

public record SubscriptionEventPayload(
        UUID userId,
        UUID assetId,
        UUID subscriptionId
) {
}
