package com.moneykk.moneytown.offering.subscription.query.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 청약 상세 조회 응답.
 */
public record SubscriptionDetailResponse(

        /**
         * 청약 ID.
         */
        UUID subscriptionId,

        /**
         * 청약 대상 공모 ID.
         */
        UUID offeringId,

        /**
         * 청약 수량.
         */
        Long quantity,

        /**
         * 청약 시점의 단위 가격 Snapshot.
         */
        Long pricePerUnit,

        /**
         * 총 청약 금액.
         */
        Long amount,

        /**
         * 현재 청약 처리 상태.
         */
        SubscriptionStatus subscriptionStatus,

        /**
         * 청약 처리 실패 코드.
         *
         * 주로 REJECTED, MANUAL_REVIEW 상태에서 사용한다.
         */
        String failureCode,

        /**
         * 정상 확정된 청약이 공모 측 사유로 취소된 경우의 취소 유형.
         */
        CancellationType cancellationType,

        /**
         * 장기 PROCESSING 상태 탐지를 위한 수량 확보 만료 시각.
         */
        Instant reservationExpiresAt,

        /**
         * CONFIRMED 상태로 전환된 시각.
         */
        Instant confirmedAt,

        /**
         * 청약 접수 시각.
         */
        Instant createdAt,

        /**
         * 최종 상태 변경 시각.
         */
        Instant updatedAt

) {

    public static SubscriptionDetailResponse from(
            Subscription subscription
    ) {
        return new SubscriptionDetailResponse(
                subscription.getSubscriptionId(),
                subscription.getOfferingId(),
                subscription.getQuantity(),
                subscription.getPricePerUnit(),
                subscription.getAmount(),
                subscription.getSubscriptionStatus(),
                subscription.getFailureCode(),
                subscription.getCancellationType(),
                subscription.getReservationExpiresAt(),
                subscription.getConfirmedAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}