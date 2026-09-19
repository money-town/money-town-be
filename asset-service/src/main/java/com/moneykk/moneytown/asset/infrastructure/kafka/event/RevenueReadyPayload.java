package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

public record RevenueReadyPayload(
        UUID assetId,
        UUID revenueId
) {
}
