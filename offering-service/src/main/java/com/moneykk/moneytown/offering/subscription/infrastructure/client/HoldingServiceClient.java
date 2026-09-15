package com.moneykk.moneytown.offering.subscription.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * 관리자 재처리 및 보상 과정에서
 * Asset Service가 관리하는 실제 지분 처리 상태를 조회한다.
 *
 * 지분 배정과 회수 같은 상태 변경은 Kafka 이벤트로 처리하며,
 * 이 클라이언트는 운영 복구를 위한 조회에만 사용한다.
 */
@FeignClient(
        name = "asset-service",
        contextId = "holdingServiceClient"
)
public interface HoldingServiceClient {

    @GetMapping(
            "/api/v1/internal/holdings/subscriptions/{subscriptionId}"
    )
    ApiResponse<HoldingSubscriptionStatusResponse>
    getHoldingSubscriptionStatus(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable("subscriptionId") UUID subscriptionId
    );
}