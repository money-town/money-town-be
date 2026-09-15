package com.moneykk.moneytown.offering.subscription.command.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 관리자 청약 재처리 요청 결과.
 */
public record SubscriptionRetryResponse(

        @Schema(
                description = "재처리 대상 청약 ID",
                example = "7cc970b5-53ae-4433-b3cc-80b523ef6801"
        )
        UUID subscriptionId,

        @Schema(
                description = """
                        재처리 요청 접수 후 청약 상태.
                        PROCESSING, HOLD_SUCCEEDED 또는 CONFIRMED가 될 수 있습니다.
                        """,
                example = "PROCESSING"
        )
        SubscriptionStatus subscriptionStatus

) {

    public static SubscriptionRetryResponse from(
            Subscription subscription
    ) {
        return new SubscriptionRetryResponse(
                subscription.getSubscriptionId(),
                subscription.getSubscriptionStatus()
        );
    }
}