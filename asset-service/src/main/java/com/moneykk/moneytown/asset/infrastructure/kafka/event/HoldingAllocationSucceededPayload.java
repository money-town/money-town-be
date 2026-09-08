package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

/** 지분 배정 성공 결과 */
public record HoldingAllocationSucceededPayload(
        UUID assetId,
        UUID holdingId,
        Long quantity,
        String result
) {
}