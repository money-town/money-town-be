package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * Holding 지분 배정 실패 결과.
 *
 * 확정된 청약의 후처리 실패를 나타낸다.
 * retryable은 재처리 가능 여부이며 자동 재시도를 실행하지 않는다.
 */
public record HoldingAllocationFailedPayload(
        UUID assetId,
        String errorCode,
        String errorMessage,
        Boolean retryable
) {
}