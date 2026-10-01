package com.moneykk.moneytown.offering.subscription.command.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SubscriptionRequestAcceptedResponse(
        @Schema(description = "비동기 청약 접수 ID")
        UUID requestId,
        @Schema(description = "접수 처리 상태", example = "QUEUED")
        SubscriptionRequestStatus status,
        @Schema(description = "처리가 완료된 경우 생성된 청약 ID", nullable = true)
        UUID subscriptionId,
        @Schema(description = "거절 또는 최종 실패 코드", nullable = true)
        String failureCode
) {
    public static SubscriptionRequestAcceptedResponse from(
            SubscriptionRequest request
    ) {
        return new SubscriptionRequestAcceptedResponse(
                request.getSubscriptionRequestId(),
                request.getRequestStatus(),
                request.getSubscriptionId(),
                request.getFailureCode()
        );
    }
}
