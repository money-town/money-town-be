package com.moneykk.moneytown.asset.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.dto.request.HoldingRevocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResult;
import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationFailedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationSucceededPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.producer.HoldingEventPublisher;
import com.moneykk.moneytown.asset.service.HoldingCommandService;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 청약 보상 이벤트를 받아 지분을 회수한다. */
@Component
@RequiredArgsConstructor
public class HoldingRevocationEventConsumer {

    private static final String EVENT_TYPE =
            "SubscriptionCompensationRequested";

    private final ObjectMapper objectMapper;
    private final HoldingQueryService holdingQueryService;
    private final HoldingCommandService holdingCommandService;
    private final HoldingEventPublisher holdingEventPublisher;

    @KafkaListener(topics = "subscription-compensation-requested")
    public void consume(String message)
            throws JsonProcessingException {

        EventEnvelope<SubscriptionCompensationRequestedPayload> event =
                readEvent(message);

        if (!EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이벤트입니다: "
                            + event.eventType()
            );
        }

        UUID subscriptionId =
                UUID.fromString(event.aggregateId());

        SubscriptionCompensationRequestedPayload payload =
                event.payload();

        try {
            HoldingSubscriptionStatusResponse status =
                    holdingQueryService.getSubscriptionStatus(
                            subscriptionId
                    );

            // 청약 이력의 자산과 이벤트의 자산이 같은지 확인
            if (status.assetId() != null
                    && !status.assetId().equals(payload.assetId())) {
                throw new BusinessException(
                        AssetErrorCode.HOLDING_DATA_CONFLICT
                );
            }

            boolean alreadyRevoked =
                    status.revocationProcessed();

            HoldingRevocationResponse response =
                    holdingCommandService.revoke(
                            status.holdingId(),
                            new HoldingRevocationRequest(
                                    subscriptionId,
                                    payload.reason()
                            )
                    );

            String noActionReason = null;
            long resultQuantity = response.quantity();

            if (response.result()
                    == HoldingRevocationResult.NO_ACTION) {
                resultQuantity = 0;
                noActionReason = alreadyRevoked
                        ? "ALREADY_REVOKED"
                        : "NOT_ALLOCATED";
            }

            holdingEventPublisher.publishRevocationSucceeded(
                    subscriptionId,
                    event.userId(),
                    event.correlationId(),
                    new HoldingRevocationSucceededPayload(
                            payload.assetId(),
                            response.holdingId(),
                            resultQuantity,
                            response.result().name(),
                            noActionReason
                    )
            );
        } catch (BusinessException exception) {
            holdingEventPublisher.publishRevocationFailed(
                    subscriptionId,
                    event.userId(),
                    event.correlationId(),
                    new HoldingRevocationFailedPayload(
                            payload.assetId(),
                            exception.getErrorCode().getCode(),
                            exception.getErrorCode().getMessage(),
                            false
                    )
            );
        }
    }

    /** JSON 문자열을 청약 보상 이벤트로 변환한다. */
    private EventEnvelope<SubscriptionCompensationRequestedPayload>
    readEvent(String message) throws JsonProcessingException {

        JavaType eventType = objectMapper
                .getTypeFactory()
                .constructParametricType(
                        EventEnvelope.class,
                        SubscriptionCompensationRequestedPayload.class
                );

        return objectMapper.readValue(message, eventType);
    }
}