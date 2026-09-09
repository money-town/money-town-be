package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

/** 지분 회수 실패 결과 */
public record HoldingRevocationFailedPayload(
        UUID assetId,
        String errorCode,
        String errorMessage,
        Boolean retryable
) {
}