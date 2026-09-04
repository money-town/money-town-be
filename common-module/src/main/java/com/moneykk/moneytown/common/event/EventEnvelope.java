package com.moneykk.moneytown.common.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String aggregateId,
        UUID userId,
        Instant occurredAt,
        String correlationId,
        T payload
) {
    public static <T> EventEnvelope<T> of(
            String eventType,
            String aggregateId,
            UUID userId,
            String correlationId,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                aggregateId,
                userId,
                Instant.now(),
                correlationId,
                payload
        );
    }
}