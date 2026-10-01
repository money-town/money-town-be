package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestProcessingService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionRequestedConsumer {

    private final ObjectMapper objectMapper;
    private final SubscriptionRequestProcessingService processingService;

    @KafkaListener(
            topics = "subscription-requested",
            groupId = "${spring.kafka.consumer.group-id}-subscription-requested",
            concurrency = "${subscription.request.consumer.concurrency:4}"
    )
    public void consume(ConsumerRecord<String, String> record)
            throws JsonProcessingException {
        EventEnvelope<SubscriptionRequestedPayload> envelope =
                objectMapper.readValue(
                        requireValue(record.value()),
                        new TypeReference<>() {}
                );

        validate(record.key(), envelope);

        try {
            MDC.put("requestId", envelope.correlationId());
            processingService.process(UUID.fromString(envelope.aggregateId()));
        } finally {
            MDC.remove("requestId");
        }
    }

    private String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("청약 접수 이벤트 본문은 필수입니다.");
        }
        return value;
    }

    private void validate(
            String key,
            EventEnvelope<SubscriptionRequestedPayload> envelope
    ) {
        if (!"SubscriptionRequested".equals(envelope.eventType())
                || envelope.payload() == null
                || envelope.userId() == null
                || envelope.aggregateId() == null) {
            throw new IllegalArgumentException("청약 접수 이벤트가 올바르지 않습니다.");
        }

        UUID.fromString(envelope.aggregateId());

        if (envelope.payload().offeringId() == null
                || !envelope.payload().offeringId().toString().equals(key)) {
            throw new IllegalArgumentException(
                    "Kafka 메시지 key는 offeringId와 일치해야 합니다."
            );
        }
    }
}
