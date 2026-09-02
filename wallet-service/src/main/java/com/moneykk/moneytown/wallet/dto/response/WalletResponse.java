package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.Wallet;

import java.time.Instant;

public record WalletResponse(
        Long walletId,
        long balance,
        long holdBalance,
        long availableBalance,
        Instant updatedAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getHoldBalance(),
                wallet.getAvailableBalance(),
                wallet.getUpdatedAt()
        );
    }
}
