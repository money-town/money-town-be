package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

/** 지분 배정 실패 결과 */
public record HoldingAllocationFailedPayload(
        UUID assetId,
        String errorCode,
        String errorMessage,
        Boolean retryable
) {
}