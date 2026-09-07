package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * Holding 지분 배정 성공 결과.
 *
 * ALLOCATED: 지분 배정 완료.
 * ALREADY_PROCESSED: 이미 배정된 청약으로, 성공으로 처리한다.
 */
public record HoldingAllocationSucceededPayload(
        UUID assetId,
        UUID holdingId,
        Long quantity,
        String result
) {
}