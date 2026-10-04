package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestStateService;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscriptionRequestedDltConsumerTest {

    @Test
    void marksRequestFailedAfterRetriesAreExhausted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SubscriptionRequestStateService stateService =
                mock(SubscriptionRequestStateService.class);
        SubscriptionRequestMetrics metrics =
                mock(SubscriptionRequestMetrics.class);
        SubscriptionRequestedDltConsumer consumer =
                new SubscriptionRequestedDltConsumer(
                        objectMapper, stateService, metrics
                );
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

        consumer.consume(new ConsumerRecord<>(
                "subscription-requested-dlt", 0, 0,
                offeringId.toString(),
                objectMapper.writeValueAsString(envelope)
        ));

        verify(stateService).fail(
                requestId,
                SubscriptionRequestedDltConsumer.EXHAUSTED_CODE
        );
        verify(metrics).recordFailed();
    }
}
