package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings/{offeringId}/subscriptions")
public class SubscriptionCommandController {

    private final SubscriptionCommandService subscriptionCommandService;

    /**
     * 선착순 청약 접수
     */
    // TODO: Gateway 인증/인가 정책 확정 후
    // INVESTOR 권한 및 사용자 정보 전달 방식 재검토
    // TODO: Idempotency-Key 처리 구현 후 필수 검증 연결
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionCreateResponse>> createSubscription(
            @PathVariable UUID offeringId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) UUID idempotencyKey,
            @Valid @RequestBody SubscriptionCreateRequest request
    ) {
        // TODO: Idempotency 구현 시 required = true로 변경하고
        // SubscriptionCommandService에 idempotencyKey 전달
        SubscriptionCreateResponse response =
                subscriptionCommandService.create(
                        offeringId,
                        userId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.success(
                                response,
                                "청약 요청이 접수되었습니다."
                        )
                );
    }
}