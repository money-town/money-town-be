package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * Holding 지분 회수 성공 결과.
 *
 * REVOKED: 실제 회수 완료.
 * NO_ACTION: 이미 회수됐거나 배정 이력이 없어 추가 회수 불필요.
 */
public record HoldingRevocationSucceededPayload(
        UUID assetId,
        UUID holdingId,
        Long quantity,
        String result,
        String noActionReason
) {
}