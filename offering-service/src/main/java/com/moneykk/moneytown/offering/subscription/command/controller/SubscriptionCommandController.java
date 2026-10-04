package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestIntakeService;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRequestAcceptedResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRequestAcceptedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    private final SubscriptionRequestIntakeService subscriptionRequestIntakeService;

    @Operation(
            summary = "선착순 청약 접수",
            description = """
                        INVESTOR의 청약 요청을 내구성 있는 접수 상태와 Outbox에 저장하고
                        202 Accepted를 반환합니다. 사용자 자격·Pre-FDS 검증, 수량 선점과
                        Wallet 동결 요청은 Kafka Consumer가 제한된 동시성으로 처리합니다.
                        같은 Idempotency-Key와 같은 요청은 최초 requestId를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "접수 저장 완료. QUEUED 상태와 requestId 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "청약 수량, Idempotency-Key 또는 요청 정보가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "INVESTOR 역할이 아닌 사용자"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "동일 Idempotency-Key에 다른 요청 해시가 전달됨"
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionRequestAcceptedResponse>> createSubscription(
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

        SubscriptionRequestAcceptedResult result =
                subscriptionRequestIntakeService.accept(
                        offeringId,
                        userId,
                        idempotencyKey,
                        request,
                        correlationId
                );

        String message = result.replayed()
                ? "이미 접수된 청약 요청입니다."
                : "청약 요청이 접수되었습니다.";

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
