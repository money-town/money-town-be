package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;

import java.time.Instant;

public record TransactionResponse(
        Long transactionId,
        Long walletId,
        WalletTransactionType type,
        long amount,
        long balanceAfter,
        Instant createdAt
) {
    public static TransactionResponse from(WalletTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt()
        );
    }
}
