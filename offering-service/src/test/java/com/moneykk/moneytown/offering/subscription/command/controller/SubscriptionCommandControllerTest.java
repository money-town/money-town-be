package com.moneykk.moneytown.offering.subscription.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCommandService;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResult;
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
class SubscriptionCommandControllerTest {

    @Mock
    private SubscriptionCommandService subscriptionCommandService;

    @InjectMocks
    private SubscriptionCommandController subscriptionCommandController;

    @Test
    @DisplayName("신규 청약이 생성되면 202 Accepted와 접수 메시지를 반환한다")
    void createsNewSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "new-key";

        SubscriptionCreateRequest request = new SubscriptionCreateRequest(10L);

        SubscriptionCreateResponse serviceResponse = new SubscriptionCreateResponse(
                UUID.randomUUID(), offeringId, 10L, 1_000L, 10_000L,
                SubscriptionStatus.PROCESSING
        );

        SubscriptionCreateResult result =
                SubscriptionCreateResult.created(serviceResponse);

        when(subscriptionCommandService.create(
                offeringId, userId, idempotencyKey, request, correlationId
        )).thenReturn(result);

        // when
        ResponseEntity<ApiResponse<SubscriptionCreateResponse>> response =
                subscriptionCommandController.createSubscription(
                        offeringId, userId, "INVESTOR",
                        correlationId, idempotencyKey, request
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("청약 요청이 접수되었습니다.");

        verify(subscriptionCommandService).create(
                offeringId, userId, idempotencyKey, request, correlationId
        );
    }

    @Test
    @DisplayName("이미 접수된 청약 요청이 재사용되면 재사용 메시지를 반환한다")
    void repliesReplayedSubscriptionRequest() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "existing-key";

        SubscriptionCreateRequest request = new SubscriptionCreateRequest(10L);

        SubscriptionCreateResponse serviceResponse = new SubscriptionCreateResponse(
                UUID.randomUUID(), offeringId, 10L, 1_000L, 10_000L,
                SubscriptionStatus.CONFIRMED
        );

        SubscriptionCreateResult result =
                SubscriptionCreateResult.replayed(serviceResponse);

        when(subscriptionCommandService.create(
                offeringId, userId, idempotencyKey, request, correlationId
        )).thenReturn(result);

        // when
        ResponseEntity<ApiResponse<SubscriptionCreateResponse>> response =
                subscriptionCommandController.createSubscription(
                        offeringId, userId, "INVESTOR",
                        correlationId, idempotencyKey, request
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("이미 접수된 청약 요청입니다.");
    }

    @Test
    @DisplayName("INVESTOR가 아니면 청약 접수를 거부한다")
    void rejectsCreateSubscriptionForNonInvestor() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(10L);

        // when & then
        assertThatThrownBy(() ->
                subscriptionCommandController.createSubscription(
                        offeringId, userId, "ADMIN",
                        UUID.randomUUID().toString(), "some-key", request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED)
                );

        verifyNoInteractions(subscriptionCommandService);
    }
}
