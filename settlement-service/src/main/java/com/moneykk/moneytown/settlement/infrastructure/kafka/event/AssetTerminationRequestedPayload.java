package com.moneykk.moneytown.settlement.infrastructure.kafka.event;

import java.time.Instant;
import java.util.UUID;

// kafka.md 3절 payload — {assetId, terminatedAt, unitPrice}
public record AssetTerminationRequestedPayload(
        UUID assetId,
        Instant terminatedAt,
        Long unitPrice
) {
}