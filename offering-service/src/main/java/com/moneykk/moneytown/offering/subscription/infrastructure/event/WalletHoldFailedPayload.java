package com.moneykk.moneytown.offering.subscription.infrastructure.event;

public record WalletHoldFailedPayload(
        Long walletId,
        String status,
        String reason
) {
}