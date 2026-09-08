package com.moneykk.moneytown.offering.subscription.command.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SubscriptionCreateResponse(

        @Schema(
                description = "생성된 청약 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID subscriptionId,

        @Schema(
                description = "청약 대상 공모 ID",
                example = "8f14e45f-ea4d-4f8b-9d5a-7b30c8d91a21"
        )
        UUID offeringId,

        @Schema(
                description = "확보된 청약 수량",
                example = "10"
        )
        Long quantity,

        @Schema(
                description = "청약 접수 시점의 공모 조각당 단위 가격을 스냅샷으로 저장한 값",
                example = "100000"
        )
        Long pricePerUnit,

        @Schema(
                description = "청약 총액. 조각당 단위 가격과 청약 수량을 곱하여 계산합니다.",
                example = "1000000"
        )
        Long amount,

        @Schema(
                description = "현재 청약 처리 상태. 접수 직후에는 PROCESSING이며 Wallet HOLD 처리 결과에 따라 변경됩니다.",
                example = "PROCESSING"
        )
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