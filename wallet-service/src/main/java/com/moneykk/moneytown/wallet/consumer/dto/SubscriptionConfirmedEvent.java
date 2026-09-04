package com.moneykk.moneytown.wallet.consumer.dto;

import java.time.Instant;
import java.util.UUID;
// SubscriptionConfirmedConsumer가 수신하는 이벤트 (Offering → Wallet, 청약 확정)


// payload에는 Holding용 필드도 오지만 Wallet은 aggregateId만으로 Hold를 찾으므로 사용하지 않는다.
public record SubscriptionConfirmedEvent(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        UUID userId,
        Instant occurredAt,
        String correlationId,
        Integer schemaVersion,
        Object payload
) {
}
