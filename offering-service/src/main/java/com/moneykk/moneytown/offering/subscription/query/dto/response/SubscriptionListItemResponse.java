package com.moneykk.moneytown.offering.subscription.query.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionListItemResponse(

        UUID subscriptionId,

        UUID offeringId,

        Long quantity,

        /**
         * 청약 시점의 조각당 단위 가격 Snapshot.
         */
        Long pricePerUnit,

        /**
         * 청약 시점의 총 청약 금액.
         *
         * pricePerUnit × quantity
         */
        Long amount,

        SubscriptionStatus subscriptionStatus,

        /**
         * 청약 처리 실패 사유 코드.
         *
         * 주로 REJECTED, MANUAL_REVIEW 상태에서 사용한다.
         */
        String failureCode,

        /**
         * 확정된 청약이 공모 측 사유로 취소된 경우의 취소 유형.
         *
         * CANCELLED 상태에서 사용한다.
         */
        CancellationType cancellationType,

        Instant createdAt,

        Instant updatedAt

) {

    public static SubscriptionListItemResponse from(
            Subscription subscription
    ) {
        return new SubscriptionListItemResponse(
                subscription.getSubscriptionId(),
                subscription.getOfferingId(),
                subscription.getQuantity(),
                subscription.getPricePerUnit(),
                subscription.getAmount(),
                subscription.getSubscriptionStatus(),
                subscription.getFailureCode(),
                subscription.getCancellationType(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}