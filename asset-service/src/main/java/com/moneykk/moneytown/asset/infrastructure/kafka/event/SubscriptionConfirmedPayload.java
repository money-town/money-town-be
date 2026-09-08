package com.moneykk.moneytown.asset.infrastructure.kafka.event;

import java.util.UUID;

/** 청약 확정 후 지분 배정에 필요한 정보 */
public record SubscriptionConfirmedPayload(
        UUID offeringId,
        UUID assetId,
        Long quantity
) {
}