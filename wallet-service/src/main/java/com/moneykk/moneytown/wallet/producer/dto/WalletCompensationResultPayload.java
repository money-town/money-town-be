package com.moneykk.moneytown.wallet.producer.dto;

import com.moneykk.moneytown.common.event.EventEnvelope;

import java.util.UUID;

// WalletEventPublisher가 발행하는 보상(UNHOLD/REFUND) 결과 이벤트의 payload
public record WalletCompensationResultPayload(
        Long holdId, Long walletId, String compensationType, Long transactionId, Long amount, String reason
) {

    public static EventEnvelope<WalletCompensationResultPayload> succeeded(
            String subscriptionId, UUID userId, String correlationId, Long holdId, Long walletId,
            String compensationType, Long transactionId, Long amount) {
        return EventEnvelope.of("WalletCompensationSucceeded", subscriptionId, userId, correlationId,
                new WalletCompensationResultPayload(holdId, walletId, compensationType, transactionId, amount, null));
    }

    public static EventEnvelope<WalletCompensationResultPayload> failed(
            String subscriptionId, UUID userId, String correlationId, Long holdId, Long walletId, String reason) {
        return EventEnvelope.of("WalletCompensationFailed", subscriptionId, userId, correlationId,
                new WalletCompensationResultPayload(holdId, walletId, null, null, null, reason));
    }
}
