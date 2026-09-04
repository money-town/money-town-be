package com.moneykk.moneytown.offering.global.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> publish(
            OutboxPublishService.ClaimedEvent event,
            String messageKey
    ) {
        Objects.requireNonNull(event, "발행할 이벤트는 필수입니다.");

        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Kafka 메시지 key는 필수입니다."
            );
        }

        return kafkaTemplate.send(
                event.topic(),
                messageKey,
                event.envelopeJson()
        );
    }
}