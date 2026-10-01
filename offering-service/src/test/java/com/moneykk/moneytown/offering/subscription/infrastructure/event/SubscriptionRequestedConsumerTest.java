package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscriptionRequestedConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SubscriptionRequestProcessingService processingService =
            mock(SubscriptionRequestProcessingService.class);
    private final SubscriptionRequestedConsumer consumer =
            new SubscriptionRequestedConsumer(objectMapper, processingService);

    @Test
    void processesValidRequestedEvent() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        var envelope = EventEnvelope.of(
                "SubscriptionRequested",
                requestId.toString(),
                UUID.randomUUID(),
                "correlation",
                new SubscriptionRequestedPayload(
                        offeringId, 1L, "key", "a".repeat(64)
                )
        );
        String json = objectMapper.writeValueAsString(envelope);

        consumer.consume(new ConsumerRecord<>(
                "subscription-requested", 0, 0,
                offeringId.toString(), json
        ));

        verify(processingService).process(requestId);
    }

    @Test
    void rejectsMismatchedOfferingPartitionKey() throws Exception {
        var envelope = EventEnvelope.of(
                "SubscriptionRequested",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                "correlation",
                new SubscriptionRequestedPayload(
                        UUID.randomUUID(), 1L, "key", "a".repeat(64)
                )
        );

        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>(
                "subscription-requested", 0, 0,
                UUID.randomUUID().toString(),
                objectMapper.writeValueAsString(envelope)
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
