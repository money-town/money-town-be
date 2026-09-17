package com.moneykk.moneytown.settlement.infrastructure.kafka.event;

import java.util.UUID;

// kafka.md 4절 payload — {assetId, revenueId}. userId는 payload가 아니라
// EventEnvelope.userId(Revenue 등록자)에 담긴다.
public record RevenueReadyPayload(
        UUID assetId,
        UUID revenueId
) {
}