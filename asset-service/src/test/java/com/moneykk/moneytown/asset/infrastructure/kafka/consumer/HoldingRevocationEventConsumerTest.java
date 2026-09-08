package com.moneykk.moneytown.asset.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.dto.request.HoldingRevocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResult;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationFailedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationSucceededPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.producer.HoldingEventPublisher;
import com.moneykk.moneytown.asset.service.HoldingCommandService;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingRevocationEventConsumerTest {

    @Mock
    private HoldingCommandService holdingCommandService;

    @Mock
    private HoldingEventPublisher holdingEventPublisher;

    private ObjectMapper objectMapper;
    private HoldingRevocationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new HoldingRevocationEventConsumer(
                objectMapper,
                holdingCommandService,
                holdingEventPublisher
        );
    }

    @Test
    @DisplayName("청약 보상 이벤트를 받으면 지분을 회수하고 성공 결과를 발행한다")
    void revokesHoldingAndPublishesSuccess() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();

        when(holdingCommandService.revokeBySubscription(
                eq(assetId), eq(userId), any()))
                .thenReturn(new HoldingRevocationResponse(
                        subscriptionId,
                        holdingId,
                        assetId,
                        userId,
                        100L,
                        HoldingRevocationResult.REVOKED,
                        null
                ));

        consumer.consume(message(subscriptionId, assetId, userId));

        ArgumentCaptor<HoldingRevocationRequest> requestCaptor =
                ArgumentCaptor.forClass(HoldingRevocationRequest.class);
        verify(holdingCommandService).revokeBySubscription(
                eq(assetId), eq(userId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().subscriptionId()).isEqualTo(subscriptionId);
        assertThat(requestCaptor.getValue().reason()).isEqualTo("OFFERING_UNDER_SUBSCRIBED");

        ArgumentCaptor<HoldingRevocationSucceededPayload> payloadCaptor =
                ArgumentCaptor.forClass(HoldingRevocationSucceededPayload.class);
        verify(holdingEventPublisher).publishRevocationSucceeded(
                eq(subscriptionId), eq(userId), eq("correlation-1"), payloadCaptor.capture());

        HoldingRevocationSucceededPayload payload = payloadCaptor.getValue();
        assertThat(payload.assetId()).isEqualTo(assetId);
        assertThat(payload.holdingId()).isEqualTo(holdingId);
        assertThat(payload.quantity()).isEqualTo(100L);
        assertThat(payload.result()).isEqualTo("REVOKED");
        assertThat(payload.noActionReason()).isNull();
    }

    @Test
    @DisplayName("이미 회수된 청약은 수량 0과 ALREADY_REVOKED를 발행한다")
    void publishesAlreadyRevokedNoAction() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();

        when(holdingCommandService.revokeBySubscription(
                eq(assetId), eq(userId), any()))
                .thenReturn(new HoldingRevocationResponse(
                        subscriptionId,
                        holdingId,
                        assetId,
                        userId,
                        0L,
                        HoldingRevocationResult.NO_ACTION,
                        "ALREADY_REVOKED"
                ));

        consumer.consume(message(subscriptionId, assetId, userId));

        ArgumentCaptor<HoldingRevocationSucceededPayload> payloadCaptor =
                ArgumentCaptor.forClass(HoldingRevocationSucceededPayload.class);
        verify(holdingEventPublisher).publishRevocationSucceeded(
                eq(subscriptionId), eq(userId), eq("correlation-1"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue().quantity()).isZero();
        assertThat(payloadCaptor.getValue().result()).isEqualTo("NO_ACTION");
        assertThat(payloadCaptor.getValue().noActionReason()).isEqualTo("ALREADY_REVOKED");
    }

    @Test
    @DisplayName("배정되지 않은 청약은 수량 0과 NOT_ALLOCATED를 발행한다")
    void publishesNotAllocatedNoAction() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(holdingCommandService.revokeBySubscription(
                eq(assetId), eq(userId), any()))
                .thenReturn(new HoldingRevocationResponse(
                        subscriptionId,
                        null,
                        null,
                        null,
                        0L,
                        HoldingRevocationResult.NO_ACTION,
                        "NOT_ALLOCATED"
                ));

        consumer.consume(message(subscriptionId, assetId, userId));

        ArgumentCaptor<HoldingRevocationSucceededPayload> payloadCaptor =
                ArgumentCaptor.forClass(HoldingRevocationSucceededPayload.class);
        verify(holdingEventPublisher).publishRevocationSucceeded(
                eq(subscriptionId), eq(userId), eq("correlation-1"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue().assetId()).isEqualTo(assetId);
        assertThat(payloadCaptor.getValue().holdingId()).isNull();
        assertThat(payloadCaptor.getValue().quantity()).isZero();
        assertThat(payloadCaptor.getValue().noActionReason()).isEqualTo("NOT_ALLOCATED");
    }

    @Test
    @DisplayName("지분 회수 중 비즈니스 오류가 발생하면 실패 결과를 발행한다")
    void publishesFailureWhenRevocationFails() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();

        when(holdingCommandService.revokeBySubscription(
                eq(assetId), eq(userId), any()))
                .thenThrow(new BusinessException(AssetErrorCode.HOLDING_DATA_CONFLICT));

        consumer.consume(message(subscriptionId, assetId, userId));

        ArgumentCaptor<HoldingRevocationFailedPayload> payloadCaptor =
                ArgumentCaptor.forClass(HoldingRevocationFailedPayload.class);
        verify(holdingEventPublisher).publishRevocationFailed(
                eq(subscriptionId), eq(userId), eq("correlation-1"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue().assetId()).isEqualTo(assetId);
        assertThat(payloadCaptor.getValue().errorCode())
                .isEqualTo("HOLDING_DATA_CONFLICT");
        assertThat(payloadCaptor.getValue().retryable()).isFalse();
        verify(holdingEventPublisher, never())
                .publishRevocationSucceeded(any(), any(), any(), any());
    }

    private String message(UUID subscriptionId, UUID assetId, UUID userId) throws Exception {
        EventEnvelope<SubscriptionCompensationRequestedPayload> event = EventEnvelope.of(
                "SubscriptionCompensationRequested",
                subscriptionId.toString(),
                userId,
                "correlation-1",
                new SubscriptionCompensationRequestedPayload(
                        UUID.randomUUID(),
                        assetId,
                        "OFFERING_UNDER_SUBSCRIBED"
                )
        );
        return objectMapper.writeValueAsString(event);
    }

}
