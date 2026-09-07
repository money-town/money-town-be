package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.HoldingRevocationResultService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HoldingRevocationResultConsumer {

    private static final TypeReference<
            EventEnvelope<HoldingRevocationSucceededPayload>
            > SUCCEEDED_TYPE = new TypeReference<>() {};

    private static final TypeReference<
            EventEnvelope<HoldingRevocationFailedPayload>
            > FAILED_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final HoldingRevocationResultService holdingRevocationResultService;

    @KafkaListener(
            topics = "holding-revocation-result",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.GROUP_ID) String consumerGroup
    ) throws JsonProcessingException {
        String json = record.value();

        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(
                    "Holding 회수 결과 이벤트 본문은 필수입니다."
            );
        }

        JsonNode root = objectMapper.readTree(json);

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Holding 회수 결과 이벤트는 JSON 객체여야 합니다."
            );
        }

        JsonNode eventTypeNode = root.get("eventType");

        if (eventTypeNode == null
                || !eventTypeNode.isTextual()
                || eventTypeNode.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "eventType은 비어 있지 않은 문자열이어야 합니다."
            );
        }

        String eventType = eventTypeNode.asText();

        switch (eventType) {
            case "HoldingRevocationSucceeded" -> {
                EventEnvelope<HoldingRevocationSucceededPayload> envelope =
                        objectMapper.readValue(json, SUCCEEDED_TYPE);

                validatePartitionKey(record.key(), envelope);

                holdingRevocationResultService.handleSucceeded(
                        envelope,
                        consumerGroup
                );
            }

            case "HoldingRevocationFailed" -> {
                EventEnvelope<HoldingRevocationFailedPayload> envelope =
                        objectMapper.readValue(json, FAILED_TYPE);

                validatePartitionKey(record.key(), envelope);

                holdingRevocationResultService.handleFailed(
                        envelope,
                        consumerGroup
                );
            }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 Holding 회수 결과 이벤트입니다. eventType="
                            + eventType
            );
        }
    }

    /**
     * Holding 결과는 청약 단위로 전달되므로
     * Kafka key가 aggregateId의 subscriptionId와 일치해야 한다.
     */
    private void validatePartitionKey(
            String key,
            EventEnvelope<?> envelope
    ) {
        if (envelope == null) {
            throw new IllegalArgumentException(
                    "envelope은 필수입니다."
            );
        }

        String aggregateId = envelope.aggregateId();

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException(
                    "aggregateId는 필수입니다."
            );
        }

        UUID subscriptionId;

        try {
            subscriptionId = UUID.fromString(aggregateId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "aggregateId는 UUID 형식의 subscriptionId여야 합니다.",
                    e
            );
        }

        if (!subscriptionId.toString().equals(key)) {
            throw new IllegalArgumentException(
                    "Kafka 메시지 key는 이벤트의 subscriptionId와 일치해야 합니다."
            );
        }
    }
}