package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.WalletCompensationResultService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletCompensationResultConsumer {

    private static final TypeReference<
            EventEnvelope<WalletCompensationResultPayload>
            > EVENT_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final WalletCompensationResultService
            walletCompensationResultService;

    @KafkaListener(
            topics = "wallet-compensation-result",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.GROUP_ID) String consumerGroup
    ) throws JsonProcessingException {
        String json = record.value();

        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(
                    "Wallet 보상 결과 이벤트 본문은 필수입니다."
            );
        }

        EventEnvelope<WalletCompensationResultPayload> envelope =
                objectMapper.readValue(json, EVENT_TYPE);

        validatePartitionKey(record.key(), envelope);

        String eventType = envelope.eventType();

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "eventType은 필수입니다."
            );
        }

        switch (eventType) {
            case "WalletCompensationSucceeded" ->
                    walletCompensationResultService.handleSucceeded(
                            envelope,
                            consumerGroup
                    );

            case "WalletCompensationFailed" ->
                    walletCompensationResultService.handleFailed(
                            envelope,
                            consumerGroup
                    );

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 Wallet 보상 결과 이벤트입니다. eventType="
                            + eventType
            );
        }
    }

    private void validatePartitionKey(
            String key,
            EventEnvelope<?> envelope
    ) {
        if (envelope == null) {
            throw new IllegalArgumentException(
                    "envelope은 필수입니다."
            );
        }

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