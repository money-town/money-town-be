package com.moneykk.moneytown.offering.subscription.command.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;

import java.util.UUID;

public record SubscriptionCreateResponse(

        /**
         * 생성된 청약 ID.
         */
        UUID subscriptionId,

        /**
         * 청약 대상 공모 ID.
         */
        UUID offeringId,

        /**
         * 확보한 청약 수량.
         */
        Long quantity,

        /**
         * 청약 시점의 조각당 단위 가격.
         *
         * Offering.pricePerUnit을 청약 시점에 Snapshot으로 저장한 값이다.
         */
        Long pricePerUnit,

        /**
         * 청약 시점의 단위 가격을 기준으로 계산한 총 청약 금액.
         *
         * pricePerUnit × quantity 값을 서버에서 계산하여
         * Subscription에 Snapshot으로 저장한 값을 반환한다.
         */
        Long amount,

        /**
         * 현재 청약 처리 상태.
         *
         * 신규 청약 접수 직후에는 PROCESSING 상태이며,
         * 이후 Wallet HOLD 처리 결과에 따라 상태가 변경될 수 있다.
         */
        SubscriptionStatus subscriptionStatus

) {

    public static SubscriptionCreateResponse from(
            Subscription subscription
    ) {
        return new SubscriptionCreateResponse(
                subscription.getSubscriptionId(),
                subscription.getOfferingId(),
                subscription.getQuantity(),
                subscription.getPricePerUnit(),
                subscription.getAmount(),
                subscription.getSubscriptionStatus()
        );
    }
}