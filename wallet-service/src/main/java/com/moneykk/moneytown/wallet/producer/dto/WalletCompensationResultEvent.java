package com.moneykk.moneytown.wallet.producer.dto;

import java.time.Instant;
import java.util.UUID;

// WalletEventPublisher가 발행하는 보상(UNHOLD/REFUND) 결과 이벤트 (topic: wallet-compensation-result)
public record WalletCompensationResultEvent(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        UUID userId,
        Instant occurredAt,
        String correlationId,
        Integer schemaVersion,
        Payload payload
) {
    public record Payload(Long holdId, Long walletId, String compensationType, Long transactionId, Long amount, String reason) {
    }

    public static WalletCompensationResultEvent succeeded(UUID subscriptionId, UUID userId, Long holdId, Long walletId,
                                                            String compensationType, Long transactionId, Long amount) {
        return new WalletCompensationResultEvent(
                UUID.randomUUID(), "WalletCompensationSucceeded", subscriptionId, userId, Instant.now(), null, 1,
                new Payload(holdId, walletId, compensationType, transactionId, amount, null)
        );
    }

    public static WalletCompensationResultEvent failed(UUID subscriptionId, UUID userId, Long holdId, Long walletId, String reason) {
        return new WalletCompensationResultEvent(
                UUID.randomUUID(), "WalletCompensationFailed", subscriptionId, userId, Instant.now(), null, 1,
                new Payload(holdId, walletId, null, null, null, reason)
        );
    }
}
