package com.moneykk.moneytown.wallet.producer.dto;

import java.time.Instant;
import java.util.UUID;

// WalletEventPublisher가 발행하는 HOLD 결과 이벤트 (topic: wallet-hold-result)
// WalletHoldSucceeded/WalletHoldFailed 둘 다 이 레코드 하나로 표현한다(같은 topic, eventType으로 구분).
public record WalletHoldResultEvent(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        UUID userId,
        Instant occurredAt,
        String correlationId,
        Integer schemaVersion,
        Payload payload
) {
    public record Payload(Long holdId, Long walletId, String status, String reason) {
    }

    public static WalletHoldResultEvent succeeded(UUID subscriptionId, UUID userId, Long holdId, Long walletId) {
        return new WalletHoldResultEvent(
                UUID.randomUUID(), "WalletHoldSucceeded", subscriptionId, userId, Instant.now(), null, 1,
                new Payload(holdId, walletId, "HELD", null)
        );
    }

    public static WalletHoldResultEvent failed(UUID subscriptionId, UUID userId, Long walletId, String reason) {
        return new WalletHoldResultEvent(
                UUID.randomUUID(), "WalletHoldFailed", subscriptionId, userId, Instant.now(), null, 1,
                new Payload(null, walletId, "FAILED", reason)
        );
    }
}
