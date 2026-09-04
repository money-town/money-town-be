package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SubscriptionEventPublisher {

    private static final String AGGREGATE_TYPE = "SUBSCRIPTION";
    private static final String RESERVED_EVENT_TYPE = "SubscriptionReserved";
    private static final String RESERVED_TOPIC = "subscription-reserved";

    private final OutboxEventStore outboxEventStore;

    public void publishReserved(
            Subscription subscription,
            String correlationId
    ) {
        Objects.requireNonNull(subscription, "subscription은 필수입니다.");

        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId는 필수입니다.");
        }

        SubscriptionReservedPayload payload =
                new SubscriptionReservedPayload(
                        subscription.getAmount()
                );

        EventEnvelope<SubscriptionReservedPayload> envelope =
                EventEnvelope.of(
                        RESERVED_EVENT_TYPE,
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        correlationId,
                        payload
                );

        outboxEventStore.save(
                AGGREGATE_TYPE,
                RESERVED_TOPIC,
                envelope
        );
    }
}