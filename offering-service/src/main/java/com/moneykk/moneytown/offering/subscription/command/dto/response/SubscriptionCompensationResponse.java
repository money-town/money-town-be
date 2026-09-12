package com.moneykk.moneytown.offering.subscription.command.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 관리자 청약 보상 요청 결과.
 */
public record SubscriptionCompensationResponse(

        @Schema(
                description = "보상 처리 대상 청약 ID",
                example = "7cc970b5-53ae-4433-b3cc-80b523ef6801"
        )
        UUID subscriptionId,

        @Schema(
                description = "현재 청약 상태",
                example = "COMPENSATING"
        )
        SubscriptionStatus subscriptionStatus

) {

    public static SubscriptionCompensationResponse from(
            Subscription subscription
    ) {
        return new SubscriptionCompensationResponse(
                subscription.getSubscriptionId(),
                subscription.getSubscriptionStatus()
        );
    }
}