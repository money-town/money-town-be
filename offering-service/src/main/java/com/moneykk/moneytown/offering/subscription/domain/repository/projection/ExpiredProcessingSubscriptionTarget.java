package com.moneykk.moneytown.offering.subscription.domain.repository.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * 예약 유효시간이 만료된 PROCESSING 청약의 조회 결과입니다.
 *
 * reservationExpiresAt과 subscriptionId는
 * 다음 배치를 조회하는 키셋 커서로 사용합니다.
 */
public record ExpiredProcessingSubscriptionTarget(
        UUID subscriptionId,
        Instant reservationExpiresAt
) {
}