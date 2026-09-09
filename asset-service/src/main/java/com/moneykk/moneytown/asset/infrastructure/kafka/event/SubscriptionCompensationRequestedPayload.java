package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

/** 청약 취소 후 지분 회수에 필요한 정보 */
public record SubscriptionCompensationRequestedPayload(
        UUID offeringId,
        UUID assetId,
        String reason
) {
}