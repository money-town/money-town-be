package com.moneykk.moneytown.asset.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResult;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationFailedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationSucceededPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.SubscriptionConfirmedPayload;
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
class HoldingAllocationEventConsumerTest {

    @Mock
    private HoldingCommandService holdingCommandService;

    @Mock
    private HoldingEventPublisher holdingEventPublisher;

    private ObjectMapper objectMapper;
    private HoldingAllocationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new HoldingAllocationEventConsumer(
                objectMapper,
                holdingCommandService,
                holdingEventPublisher
        );
    }

    @Test
    @DisplayName("청약 확정 이벤트를 받으면 지분을 배정하고 성공 결과를 발행한다")
    void allocatesHoldingAndPublishesSuccess() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();

        EventEnvelope<SubscriptionConfirmedPayload> event = EventEnvelope.of(
                "SubscriptionConfirmed",
                subscriptionId.toString(),
                userId,
                "correlation-1",
                new SubscriptionConfirmedPayload(offeringId, assetId, 100L)
        );

        when(holdingCommandService.allocate(any()))
                .thenReturn(new HoldingAllocationResponse(
                        subscriptionId,
                        holdingId,
                        assetId,
                        userId,
                        100L,
                        HoldingAllocationResult.ALLOCATED
                ));

        consumer.consume(objectMapper.writeValueAsString(event));

        ArgumentCaptor<HoldingAllocationRequest> requestCaptor =
                ArgumentCaptor.forClass(HoldingAllocationRequest.class);
        verify(holdingCommandService).allocate(requestCaptor.capture());

        HoldingAllocationRequest request = requestCaptor.getValue();
        assertThat(request.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(request.assetId()).isEqualTo(assetId);
        assertThat(request.userId()).isEqualTo(userId);
        assertThat(request.quantity()).isEqualTo(100L);

        ArgumentCaptor<HoldingAllocationSucceededPayload> payloadCaptor =
                ArgumentCaptor.forClass(HoldingAllocationSucceededPayload.class);
        verify(holdingEventPublisher).publishAllocationSucceeded(
                eq(subscriptionId),
                eq(userId),
                eq("correlation-1"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue().assetId()).isEqualTo(assetId);
        assertThat(payloadCaptor.getValue().holdingId()).isEqualTo(holdingId);
        assertThat(payloadCaptor.getValue().quantity()).isEqualTo(100L);
        assertThat(payloadCaptor.getValue().result()).isEqualTo("ALLOCATED");
        verify(holdingEventPublisher, never())
                .publishAllocationFailed(any(), any(), any(), any());
    }

    @Test
    @DisplayName("지분 배정 중 비즈니스 오류가 발생하면 실패 결과를 발행한다")
    void publishesFailureWhenAllocationFails() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        EventEnvelope<SubscriptionConfirmedPayload> event = EventEnvelope.of(
                "SubscriptionConfirmed",
                subscriptionId.toString(),
                userId,
                "correlation-2",
                new SubscriptionConfirmedPayload(UUID.randomUUID(), assetId, 100L)
        );

        when(holdingCommandService.allocate(any()))
                .thenThrow(new BusinessException(AssetErrorCode.ASSET_NOT_AVAILABLE));

        consumer.consume(objectMapper.writeValueAsString(event));

        ArgumentCaptor<HoldingAllocationFailedPayload> payloadCaptor =
                ArgumentCaptor.forClass(HoldingAllocationFailedPayload.class);
        verify(holdingEventPublisher).publishAllocationFailed(
                eq(subscriptionId),
                eq(userId),
                eq("correlation-2"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue().assetId()).isEqualTo(assetId);
        assertThat(payloadCaptor.getValue().errorCode()).isEqualTo("ASSET_409_03");
        assertThat(payloadCaptor.getValue().retryable()).isFalse();
        verify(holdingEventPublisher, never())
                .publishAllocationSucceeded(any(), any(), any(), any());
    }
}
