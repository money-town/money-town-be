package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * Wallet HOLD 실패로 청약이 최종 거절됐을 때
 * Analysis PostFDS로 전달하는 이벤트 Payload.
 */
public record SubscriptionFailedPayload(
        UUID userId,
        UUID assetId,
        UUID subscriptionId,
        String failureCode
) {
}