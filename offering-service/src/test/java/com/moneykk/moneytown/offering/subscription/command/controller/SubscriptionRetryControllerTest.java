package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRetryCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRetryControllerTest {

    @Mock
    private SubscriptionRetryCommandService
            subscriptionRetryCommandService;

    @InjectMocks
    private SubscriptionRetryController
            subscriptionRetryController;

    @Test
    @DisplayName("관리자의 최초 청약 재처리 요청은 202 Accepted를 반환한다")
    void returnsAcceptedForNewRetryRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "retry-key";
        String correlationId = UUID.randomUUID().toString();

        SubscriptionRetryResponse serviceResponse =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.PROCESSING
                );

        when(subscriptionRetryCommandService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId
        )).thenReturn(
                SubscriptionRetryResult.created(
                        serviceResponse
                )
        );

        // when
        ResponseEntity<
                ApiResponse<SubscriptionRetryResponse>
                > response =
                subscriptionRetryController.retrySubscription(
                        subscriptionId,
                        adminId,
                        "ADMIN",
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<SubscriptionRetryResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.message())
                .isEqualTo("청약 재처리 요청이 접수되었습니다.");
        assertThat(body.code()).isNull();
        assertThat(body.data()).isEqualTo(serviceResponse);
        assertThat(body.data().subscriptionId())
                .isEqualTo(subscriptionId);
        assertThat(body.data().subscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        verify(subscriptionRetryCommandService)
                .retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );
    }

    @Test
    @DisplayName("동일 멱등키로 완료된 재처리 요청은 기존 결과 메시지를 반환한다")
    void returnsReplayMessageForCompletedRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "replayed-retry-key";
        String correlationId = UUID.randomUUID().toString();

        SubscriptionRetryResponse serviceResponse =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.HOLD_SUCCEEDED
                );

        when(subscriptionRetryCommandService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId
        )).thenReturn(
                SubscriptionRetryResult.replayed(
                        serviceResponse
                )
        );

        // when
        ResponseEntity<
                ApiResponse<SubscriptionRetryResponse>
                > response =
                subscriptionRetryController.retrySubscription(
                        subscriptionId,
                        adminId,
                        "ADMIN",
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<SubscriptionRetryResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.message())
                .isEqualTo(
                        "이미 처리된 청약 재처리 요청입니다."
                );
        assertThat(body.code()).isNull();
        assertThat(body.data()).isEqualTo(serviceResponse);
    }

    @Test
    @DisplayName("Correlation-ID가 없어도 관리자 재처리 요청을 전달한다")
    void acceptsRequestWithoutCorrelationId() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey =
                "missing-correlation-key";

        SubscriptionRetryResponse serviceResponse =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.PROCESSING
                );

        when(subscriptionRetryCommandService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                null
        )).thenReturn(
                SubscriptionRetryResult.created(
                        serviceResponse
                )
        );

        // when
        ResponseEntity<
                ApiResponse<SubscriptionRetryResponse>
                > response =
                subscriptionRetryController.retrySubscription(
                        subscriptionId,
                        adminId,
                        "ADMIN",
                        idempotencyKey,
                        null
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();

        verify(subscriptionRetryCommandService)
                .retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        null
                );
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 청약 재처리 요청을 거부한다")
    void rejectsRetryWhenUserIsNotAdmin() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "non-admin-key";
        String correlationId = UUID.randomUUID().toString();

        // when & then
        assertThatThrownBy(() ->
                subscriptionRetryController.retrySubscription(
                        subscriptionId,
                        userId,
                        "INVESTOR",
                        idempotencyKey,
                        correlationId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_ACCESS_DENIED
                        )
                );

        verifyNoInteractions(
                subscriptionRetryCommandService
        );
    }

    @Test
    @DisplayName("소문자로 전달된 ADMIN 권한도 허용한다")
    void acceptsLowercaseAdminRole() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "lowercase-admin-key";
        String correlationId = UUID.randomUUID().toString();

        SubscriptionRetryResponse serviceResponse =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.CONFIRMED
                );

        when(subscriptionRetryCommandService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId
        )).thenReturn(
                SubscriptionRetryResult.created(
                        serviceResponse
                )
        );

        // when
        ResponseEntity<
                ApiResponse<SubscriptionRetryResponse>
                > response =
                subscriptionRetryController.retrySubscription(
                        subscriptionId,
                        adminId,
                        "admin",
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        verify(subscriptionRetryCommandService)
                .retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );
    }
}