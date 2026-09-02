package com.moneykk.moneytown.asset.dto.response;

import java.util.UUID;

/** 지분 배정 응답 */
public record HoldingAllocationResponse(
        UUID subscriptionId,
        UUID holdingId,
        UUID assetId,
        UUID userId,
        long quantity,
        HoldingAllocationResult result
) {
}