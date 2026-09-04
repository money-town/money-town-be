package com.moneykk.moneytown.offering.subscription.infrastructure.event;

public record WalletHoldSucceededPayload(
        Long holdId,
        Long walletId,
        String status
) {
}