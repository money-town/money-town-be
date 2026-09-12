package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResult;
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
 * 관리자의 청약 복구 및 보상 명령 API.
 */
@Tag(
        name = "청약 관리",
        description = "관리자의 청약 재처리 및 보상 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionCompensationController {

    private final SubscriptionCompensationCommandService
            subscriptionCompensationCommandService;

    /**
     * MANUAL_REVIEW 상태의 청약에 대해
     * Wallet과 Holding의 실제 처리 상태를 확인하고
     * 필요한 보상 처리를 다시 시작한다.
     */
    @Operation(
            summary = "관리자 청약 보상 처리",
            description = """
                    ADMIN이 MANUAL_REVIEW 상태의 청약에 대해 보상을 요청합니다.
                    Wallet과 Holding의 실제 처리 상태를 조회한 후
                    아직 완료되지 않은 보상 처리만 다시 요청합니다.
                    동일한 Idempotency-Key로 요청하면 기존 결과를 반환합니다.
                    """
    )
    @PostMapping("/{subscriptionId}/compensation")
    public ResponseEntity<
            ApiResponse<SubscriptionCompensationResponse>
            > compensateSubscription(
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

        SubscriptionCompensationResult result =
                subscriptionCompensationCommandService.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        String message = result.replayed()
                ? "이미 처리된 청약 보상 요청입니다."
                : "청약 보상 요청이 접수되었습니다.";

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