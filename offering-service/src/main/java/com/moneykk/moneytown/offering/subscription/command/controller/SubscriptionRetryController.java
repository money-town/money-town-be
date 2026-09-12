package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRetryCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 관리자의 청약 재처리 명령 API.
 */
@Tag(
        name = "청약 관리",
        description = "관리자의 청약 재처리 및 보상 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionRetryController {

    private final SubscriptionRetryCommandService
            subscriptionRetryCommandService;

    /**
     * 자동 처리가 완료되지 않은 청약에 대해
     * Wallet과 Holding의 실제 상태를 확인하고
     * 필요한 처리만 다시 요청한다.
     */
    @Operation(
            summary = "관리자 청약 재처리",
            description = """
                    ADMIN이 자동 처리가 완료되지 않은 청약의 재처리를 요청합니다.
                    
                    Wallet과 Holding의 실제 처리 상태를 조회한 후
                    아직 완료되지 않은 단계만 다시 처리합니다.
                    
                    재처리 대상은 다음과 같습니다.
                    - MANUAL_REVIEW 상태의 청약
                    - CONFIRMED 상태이면서 Holding 배정에 실패한 청약
                    
                    동일한 Idempotency-Key로 다시 요청하면
                    최초 요청의 처리 결과를 반환합니다.
                    """
    )
    @PostMapping("/{subscriptionId}/retry")
    public ResponseEntity<
            ApiResponse<SubscriptionRetryResponse>
            > retrySubscription(
            @PathVariable UUID subscriptionId,

            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID adminId,

            @RequestHeader(AuthHeaderConstants.USER_ROLE)
            String role,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @RequestHeader(
                    value = AuthHeaderConstants.CORRELATION_ID,
                    required = false
            )
            String correlationId
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
            );
        }

        SubscriptionRetryResult result =
                subscriptionRetryCommandService.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        String message = result.replayed()
                ? "이미 처리된 청약 재처리 요청입니다."
                : "청약 재처리 요청이 접수되었습니다.";

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.success(
                                result.response(),
                                message
                        )
                );
    }
}