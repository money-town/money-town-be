package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import java.util.UUID;

/**
 * 사용자가 공모의 1인당 최대 청약 수량을 초과하여 요청했을 때
 * Analysis PostFDS로 전달하는 이벤트 Payload.
 *
 * 청약 엔티티가 생성되기 전에 발생하므로
 * subscriptionId는 null로 전달한다.
 */
public record SubscriptionLimitExceededPayload(
        UUID userId,
        UUID assetId,
        UUID subscriptionId,
        Long requestedQuantity,
        Long maxSubscriptionQuantity
) {
}