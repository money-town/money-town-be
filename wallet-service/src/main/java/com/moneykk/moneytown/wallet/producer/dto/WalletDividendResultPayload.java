package com.moneykk.moneytown.wallet.producer.dto;

import com.moneykk.moneytown.common.event.EventEnvelope;

import java.util.UUID;

// 배당 지급 결과 payload — Succeeded/Failed 둘 다 이 레코드 하나로 표현(eventType으로 구분)
public record WalletDividendResultPayload(
        UUID payoutId, UUID settlementBatchId, Long walletId, Long transactionId, String reason
) {

    public static EventEnvelope<WalletDividendResultPayload> succeeded(
            UUID payoutId, UUID settlementBatchId, UUID investorId, String correlationId,
            Long walletId, Long transactionId) {
        return EventEnvelope.of("DividendPayoutDispatchSucceeded", payoutId.toString(), investorId, correlationId,
                new WalletDividendResultPayload(payoutId, settlementBatchId, walletId, transactionId, null));
    }

    public static EventEnvelope<WalletDividendResultPayload> failed(
            UUID payoutId, UUID settlementBatchId, UUID investorId, String correlationId,
            Long walletId, String reason) {
        return EventEnvelope.of("DividendPayoutDispatchFailed", payoutId.toString(), investorId, correlationId,
                new WalletDividendResultPayload(payoutId, settlementBatchId, walletId, null, reason));
    }
}
