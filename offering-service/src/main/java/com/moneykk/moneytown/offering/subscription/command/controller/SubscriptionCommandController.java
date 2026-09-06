package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(
        name = "청약 명령",
        description = "투자자의 청약 접수 및 처리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings/{offeringId}/subscriptions")
public class SubscriptionCommandController {

    private final SubscriptionCommandService subscriptionCommandService;

    @Operation(
            summary = "선착순 청약 접수",
            description = """
                        INVESTOR가 모집 중인 공모에 청약을 요청합니다.
                        사용자 자격, PreFDS 및 청약 수량을 검증한 후 수량을 확보하고,
                        Wallet에 자금 동결을 비동기로 요청합니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionCreateResponse>> createSubscription(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestHeader(AuthHeaderConstants.CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubscriptionCreateRequest request
    ) {
        if (!"INVESTOR".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
            );
        }

        SubscriptionCreateResponse response =
                subscriptionCommandService.create(
                        offeringId,
                        userId,
                        idempotencyKey,
                        request,
                        correlationId
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