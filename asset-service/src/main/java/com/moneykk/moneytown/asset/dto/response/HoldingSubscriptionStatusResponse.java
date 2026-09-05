package com.moneykk.moneytown.asset.dto.response;

import java.time.Instant;
import java.util.UUID;

/** 청약별 지분 처리 상태 응답 */
public record HoldingSubscriptionStatusResponse(
        UUID subscriptionId,
        UUID holdingId,
        UUID assetId,
        UUID userId,
        long allocatedQuantity,
        long revokedQuantity,
        boolean allocationProcessed,
        boolean revocationProcessed,
        Instant lastProcessedAt
) {
}
