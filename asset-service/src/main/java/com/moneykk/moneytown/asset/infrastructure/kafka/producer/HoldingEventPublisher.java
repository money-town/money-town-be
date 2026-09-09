package com.moneykk.moneytown.asset.infrastructure.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationFailedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationSucceededPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationFailedPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationSucceededPayload;
import com.moneykk.moneytown.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 지분 처리 결과를 Offering 서비스에 전달한다. */
@Component
@RequiredArgsConstructor
public class HoldingEventPublisher {

    private static final String ALLOCATION_RESULT_TOPIC =
            "holding-allocation-result";

    private static final String REVOCATION_RESULT_TOPIC =
            "holding-revocation-result";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 지분 배정 성공 결과를 발행한다. */
    public void publishAllocationSucceeded(
            UUID subscriptionId,
            UUID userId,
            String correlationId,
            HoldingAllocationSucceededPayload payload
    ) {
        EventEnvelope<HoldingAllocationSucceededPayload> event =
                EventEnvelope.of(
                        "HoldingAllocationSucceeded",
                        subscriptionId.toString(),
                        userId,
                        correlationId,
                        payload
                );

        send(ALLOCATION_RESULT_TOPIC, subscriptionId, event);
    }

    /** 지분 배정 실패 결과를 발행한다. */
    public void publishAllocationFailed(
            UUID subscriptionId,
            UUID userId,
            String correlationId,
            HoldingAllocationFailedPayload payload
    ) {
        EventEnvelope<HoldingAllocationFailedPayload> event =
                EventEnvelope.of(
                        "HoldingAllocationFailed",
                        subscriptionId.toString(),
                        userId,
                        correlationId,
                        payload
                );

        send(ALLOCATION_RESULT_TOPIC, subscriptionId, event);
    }

    /** 지분 회수 성공 결과를 발행한다. */
    public void publishRevocationSucceeded(
            UUID subscriptionId,
            UUID userId,
            String correlationId,
            HoldingRevocationSucceededPayload payload
    ) {
        EventEnvelope<HoldingRevocationSucceededPayload> event =
                EventEnvelope.of(
                        "HoldingRevocationSucceeded",
                        subscriptionId.toString(),
                        userId,
                        correlationId,
                        payload
                );

        send(REVOCATION_RESULT_TOPIC, subscriptionId, event);
    }

    /** 지분 회수 실패 결과를 발행한다. */
    public void publishRevocationFailed(
            UUID subscriptionId,
            UUID userId,
            String correlationId,
            HoldingRevocationFailedPayload payload
    ) {
        EventEnvelope<HoldingRevocationFailedPayload> event =
                EventEnvelope.of(
                        "HoldingRevocationFailed",
                        subscriptionId.toString(),
                        userId,
                        correlationId,
                        payload
                );

        send(REVOCATION_RESULT_TOPIC, subscriptionId, event);
    }

    /** 이벤트를 JSON으로 변환하여 Kafka에 전송한다. */
    private void send(
            String topic,
            UUID subscriptionId,
            EventEnvelope<?> event
    ) {
        try {
            String message =
                    objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    topic,
                    subscriptionId.toString(),
                    message
            ).join();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "지분 처리 결과 직렬화에 실패했습니다.",
                    exception
            );
        }
    }
}