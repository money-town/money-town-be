package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record AssetTerminationRequestedPayload(
        UUID assetId,
        Instant terminatedAt,
        Long unitPrice
) {
}
