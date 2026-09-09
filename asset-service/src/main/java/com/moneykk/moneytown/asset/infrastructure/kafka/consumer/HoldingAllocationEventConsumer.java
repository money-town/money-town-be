package com.moneykk.moneytown.asset.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationFailedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationSucceededPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.SubscriptionConfirmedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.producer.HoldingEventPublisher;
import com.moneykk.moneytown.asset.service.HoldingCommandService;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 청약 확정 이벤트를 받아 지분을 배정한다.
 */
@Component
@RequiredArgsConstructor
public class HoldingAllocationEventConsumer {

    private static final String EVENT_TYPE =
            "SubscriptionConfirmed";

    private final ObjectMapper objectMapper;
    private final HoldingCommandService holdingCommandService;
    private final HoldingEventPublisher holdingEventPublisher;

    @KafkaListener(topics = "subscription-confirmed")
    public void consume(String message)
            throws JsonProcessingException {

        EventEnvelope<SubscriptionConfirmedPayload> event =
                readEvent(message);

        if (!EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이벤트입니다: "
                            + event.eventType()
            );
        }

        UUID subscriptionId =
                UUID.fromString(event.aggregateId());

        SubscriptionConfirmedPayload payload =
                event.payload();

        try {
            HoldingAllocationResponse response =
                    holdingCommandService.allocate(
                            new HoldingAllocationRequest(
                                    subscriptionId,
                                    payload.assetId(),
                                    event.userId(),
                                    payload.quantity()
                            )
                    );

            holdingEventPublisher.publishAllocationSucceeded(
                    subscriptionId,
                    event.userId(),
                    event.correlationId(),
                    new HoldingAllocationSucceededPayload(
                            response.assetId(),
                            response.holdingId(),
                            response.quantity(),
                            response.result().name()
                    )
            );
        } catch (BusinessException exception) {
            String errorCode =
                    exception.getErrorCode() instanceof AssetErrorCode assetErrorCode
                            ? assetErrorCode.name()
                            : exception.getErrorCode().getCode();

            holdingEventPublisher.publishAllocationFailed(
                    subscriptionId,
                    event.userId(),
                    event.correlationId(),
                    new HoldingAllocationFailedPayload(
                            payload.assetId(),
                            errorCode,
                            exception.getErrorCode().getMessage(),
                            false
                    )
            );
        }
    }

    /**
     * JSON 문자열을 청약 확정 이벤트로 변환한다.
     */
    private EventEnvelope<SubscriptionConfirmedPayload> readEvent(
            String message
    ) throws JsonProcessingException {

        JavaType eventType = objectMapper
                .getTypeFactory()
                .constructParametricType(
                        EventEnvelope.class,
                        SubscriptionConfirmedPayload.class
                );

        return objectMapper.readValue(message, eventType);
    }
}