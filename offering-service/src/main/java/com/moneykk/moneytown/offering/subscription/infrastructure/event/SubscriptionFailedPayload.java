package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * Post FDS 집계 대상으로 전달하는 청약 실패 이벤트 Payload.
 *
 * failureSource는 실패가 발생한 업무 영역을 나타내며,
 * failureReasonCode에는 해당 도메인에서 확정한 실패 코드 이름을 전달한다.
 */
public record SubscriptionFailedPayload(
        UUID userId,
        UUID assetId,
        UUID subscriptionId,
        String failureSource,
        String failureReasonCode
) {
}