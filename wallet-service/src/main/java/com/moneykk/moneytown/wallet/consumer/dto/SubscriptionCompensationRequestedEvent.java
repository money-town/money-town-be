package com.moneykk.moneytown.wallet.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;
// SubscriptionCompensationRequestedConsumer가 수신하는 이벤트 (Offering → Wallet, 보상 트리거)

public record SubscriptionCompensationRequestedEvent(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        UUID userId,
        Instant occurredAt,
        String correlationId,
        Integer schemaVersion,
        Payload payload
) {
    // Holding용 필드도 같이 오지만 Wallet은 reason만 사용.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String reason) {
    }
}
