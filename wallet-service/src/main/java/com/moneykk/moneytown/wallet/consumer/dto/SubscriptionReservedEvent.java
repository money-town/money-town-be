package com.moneykk.moneytown.wallet.consumer.dto;

import java.time.Instant;
import java.util.UUID;
// SubscriptionReservedConsumer가 수신하는 이벤트 (Offering → Wallet, 청약 예약)

public record SubscriptionReservedEvent(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        UUID userId,
        Instant occurredAt,
        String correlationId,
        Integer schemaVersion,
        Payload payload
) {
    public record Payload(long amount) {
    }
}
