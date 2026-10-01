package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestStateService;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionRequestedDltConsumer {

    static final String EXHAUSTED_CODE = "ASYNC_PROCESSING_RETRIES_EXHAUSTED";

    private final ObjectMapper objectMapper;
    private final SubscriptionRequestStateService stateService;
    private final SubscriptionRequestMetrics metrics;

    @KafkaListener(
            topics = "subscription-requested-dlt",
            groupId = "${spring.kafka.consumer.group-id}-subscription-requested-dlt"
    )
    public void consume(ConsumerRecord<String, String> record)
            throws JsonProcessingException {
        EventEnvelope<SubscriptionRequestedPayload> envelope =
                objectMapper.readValue(
                        record.value(),
                        new TypeReference<>() {}
                );

        if (!"SubscriptionRequested".equals(envelope.eventType())
                || envelope.payload() == null
                || envelope.payload().offeringId() == null
                || !envelope.payload().offeringId().toString().equals(record.key())) {
            throw new IllegalArgumentException("DLT 청약 접수 이벤트가 올바르지 않습니다.");
        }

        UUID requestId = UUID.fromString(envelope.aggregateId());
        stateService.fail(requestId, EXHAUSTED_CODE);
        metrics.recordFailed();
    }
}
