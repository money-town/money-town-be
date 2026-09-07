package com.moneykk.moneytown.offering.subscription.query.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionListItemResponse(


        @Schema(
                description = "청약 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID subscriptionId,

        @Schema(
                description = "청약 대상 공모 ID",
                example = "8f14e45f-ea4d-4f8b-9d5a-7b30c8d91a21"
        )
        UUID offeringId,

        @Schema(
                description = "청약 수량",
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
                description = "현재 청약 처리 상태",
                example = "CONFIRMED"
        )
        SubscriptionStatus subscriptionStatus,

        @Schema(
                description = "청약 처리 실패 사유 코드. Wallet HOLD 실패 시 WalletHoldFailed.reason을 저장하며, 타임아웃 등 Offering 내부 실패에서는 해당 도메인 코드를 저장합니다.",
                example = "INSUFFICIENT_AVAILABLE_BALANCE"
        )
        String failureCode,

        @Schema(
                description = "확정된 청약이 공모 측 사유로 취소된 경우의 취소 유형. CANCELLED 상태에서 사용됩니다.",
                example = "OFFERING_ADMIN_CANCELLED"
        )
        CancellationType cancellationType,

        @Schema(
                description = "청약 접수 시각",
                example = "2026-09-10T09:05:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "청약 최종 수정 시각",
                example = "2026-09-10T09:06:00Z"
        )
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