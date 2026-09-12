package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResult;
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
class SubscriptionCompensationControllerTest {

    @Mock
    private SubscriptionCompensationCommandService
            subscriptionCompensationCommandService;

    @InjectMocks
    private SubscriptionCompensationController
            subscriptionCompensationController;

    @Test
    @DisplayName("관리자의 최초 청약 보상 요청은 202 Accepted를 반환한다")
    void returnsAcceptedForNewCompensationRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "compensation-key";
        String correlationId = UUID.randomUUID().toString();

        SubscriptionCompensationResponse serviceResponse =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        SubscriptionCompensationResult serviceResult =
                SubscriptionCompensationResult.created(
                        serviceResponse
                );

        when(subscriptionCompensationCommandService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId
        )).thenReturn(serviceResult);

        // when
        ResponseEntity<
                ApiResponse<SubscriptionCompensationResponse>
                > response =
                subscriptionCompensationController
                        .compensateSubscription(
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

        ApiResponse<SubscriptionCompensationResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.message())
                .isEqualTo("청약 보상 요청이 접수되었습니다.");
        assertThat(body.code()).isNull();

        assertThat(body.data()).isEqualTo(serviceResponse);
        assertThat(body.data().subscriptionId())
                .isEqualTo(subscriptionId);
        assertThat(body.data().subscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        verify(subscriptionCompensationCommandService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );
    }

    @Test
    @DisplayName("동일 멱등키로 완료된 보상 요청은 기존 결과 메시지를 반환한다")
    void returnsReplayMessageForCompletedRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "replayed-key";
        String correlationId = UUID.randomUUID().toString();

        SubscriptionCompensationResponse serviceResponse =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        SubscriptionCompensationResult serviceResult =
                SubscriptionCompensationResult.replayed(
                        serviceResponse
                );

        when(subscriptionCompensationCommandService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId
        )).thenReturn(serviceResult);

        // when
        ResponseEntity<
                ApiResponse<SubscriptionCompensationResponse>
                > response =
                subscriptionCompensationController
                        .compensateSubscription(
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

        ApiResponse<SubscriptionCompensationResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.message())
                .isEqualTo("이미 처리된 청약 보상 요청입니다.");
        assertThat(body.code()).isNull();
        assertThat(body.data()).isEqualTo(serviceResponse);

        verify(subscriptionCompensationCommandService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );
    }

    @Test
    @DisplayName("Correlation-ID가 없어도 관리자 보상 요청을 전달한다")
    void acceptsRequestWithoutCorrelationId() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "missing-correlation-key";

        SubscriptionCompensationResponse serviceResponse =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        when(subscriptionCompensationCommandService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                null
        )).thenReturn(
                SubscriptionCompensationResult.created(
                        serviceResponse
                )
        );

        // when
        ResponseEntity<
                ApiResponse<SubscriptionCompensationResponse>
                > response =
                subscriptionCompensationController
                        .compensateSubscription(
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

        verify(subscriptionCompensationCommandService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        null
                );
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 청약 보상 요청을 거부한다")
    void rejectsCompensationWhenUserIsNotAdmin() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String idempotencyKey = "non-admin-key";
        String correlationId = UUID.randomUUID().toString();

        // when & then
        assertThatThrownBy(() ->
                subscriptionCompensationController
                        .compensateSubscription(
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

        // 권한 검증에서 차단되므로 서비스는 호출되지 않는다.
        verifyNoInteractions(
                subscriptionCompensationCommandService
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

        SubscriptionCompensationResponse serviceResponse =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        when(subscriptionCompensationCommandService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId
        )).thenReturn(
                SubscriptionCompensationResult.created(
                        serviceResponse
                )
        );

        // when
        ResponseEntity<
                ApiResponse<SubscriptionCompensationResponse>
                > response =
                subscriptionCompensationController
                        .compensateSubscription(
                                subscriptionId,
                                adminId,
                                "admin",
                                idempotencyKey,
                                correlationId
                        );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        verify(subscriptionCompensationCommandService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );
    }
}