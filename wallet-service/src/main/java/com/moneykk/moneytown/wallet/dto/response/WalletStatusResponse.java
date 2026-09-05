package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.Wallet;

public record WalletStatusResponse(
        Long walletId,
        long holdBalance,
        long availableBalance,
        boolean hasActiveHold
) {
    public static WalletStatusResponse of(Wallet wallet, boolean hasActiveHold) {
        return new WalletStatusResponse(
                wallet.getId(),
                wallet.getHoldBalance(),
                wallet.getAvailableBalance(),
                hasActiveHold
        );
    }
}
