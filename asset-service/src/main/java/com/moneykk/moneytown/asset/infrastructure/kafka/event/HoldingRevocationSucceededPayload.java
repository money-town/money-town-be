package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

/** 지분 회수 성공 결과 */
public record HoldingRevocationSucceededPayload(
        UUID assetId,
        UUID holdingId,
        Long quantity,
        String result,
        String noActionReason
) {
}