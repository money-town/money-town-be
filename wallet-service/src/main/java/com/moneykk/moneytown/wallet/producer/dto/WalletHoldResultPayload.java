package com.moneykk.moneytown.wallet.producer.dto;

import com.moneykk.moneytown.common.event.EventEnvelope;

import java.util.UUID;

// WalletEventPublisher가 발행하는 HOLD 결과 이벤트의 payload
// WalletHoldSucceeded/WalletHoldFailed 둘 다 이 레코드 하나로 표현한다(같은 topic, eventType으로 구분).
public record WalletHoldResultPayload(Long holdId, Long walletId, String status, String reason) {

    public static EventEnvelope<WalletHoldResultPayload> succeeded(
            String subscriptionId, UUID userId, String correlationId, Long holdId, Long walletId) {
        return EventEnvelope.of("WalletHoldSucceeded", subscriptionId, userId, correlationId,
                new WalletHoldResultPayload(holdId, walletId, "HELD", null));
    }

    public static EventEnvelope<WalletHoldResultPayload> failed(
            String subscriptionId, UUID userId, String correlationId, Long walletId, String reason) {
        return EventEnvelope.of("WalletHoldFailed", subscriptionId, userId, correlationId,
                new WalletHoldResultPayload(null, walletId, "FAILED", reason));
    }
}
