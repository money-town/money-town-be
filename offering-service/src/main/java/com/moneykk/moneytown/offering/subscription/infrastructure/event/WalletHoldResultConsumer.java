package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.WalletHoldResultService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletHoldResultConsumer {

    private final ObjectMapper objectMapper;
    private final WalletHoldResultService walletHoldResultService;

    @KafkaListener(
            topics = "wallet-hold-result",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.GROUP_ID) String consumerGroup
    ) throws JsonProcessingException {
        String json = record.value();

        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(
                    "동결 결과 이벤트 본문은 필수입니다."
            );
        }

        JsonNode root = objectMapper.readTree(json);

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "동결 결과 이벤트는 JSON 객체여야 합니다."
            );
        }

        String eventType = root.path("eventType").asText("");

        switch (eventType) {
            case "WalletHoldSucceeded" -> {
                EventEnvelope<WalletHoldSucceededPayload> envelope =
                        objectMapper.readValue(
                                json,
                                new TypeReference<
                                        EventEnvelope<WalletHoldSucceededPayload>
                                        >() {}
                        );

                validatePartitionKey(record.key(), envelope);

                walletHoldResultService.handleSucceeded(
                        envelope,
                        consumerGroup
                );
            }

            case "WalletHoldFailed" -> {
                EventEnvelope<WalletHoldFailedPayload> envelope =
                        objectMapper.readValue(
                                json,
                                new TypeReference<
                                        EventEnvelope<WalletHoldFailedPayload>
                                        >() {}
                        );

                validatePartitionKey(record.key(), envelope);

                walletHoldResultService.handleFailed(
                        envelope,
                        consumerGroup
                );
            }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 동결 결과 이벤트입니다. eventType="
                            + eventType
            );
        }
    }

    private void validatePartitionKey(
            String key,
            EventEnvelope<?> envelope
    ) {
        if (envelope.userId() == null) {
            throw new IllegalArgumentException(
                    "userId는 필수입니다."
            );
        }

        if (!envelope.userId().toString().equals(key)) {
            throw new IllegalArgumentException(
                    "Kafka 메시지 key는 이벤트의 userId와 일치해야 합니다."
            );
        }
    }
}