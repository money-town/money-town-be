package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;

import java.time.Instant;

public record TransactionListItemResponse(
        Long transactionId,
        WalletTransactionType type,
        long amount,
        long balanceBefore,
        long balanceAfter,
        String referenceId,
        Instant createdAt
) {
    public static TransactionListItemResponse from(WalletTransaction transaction) {
        return new TransactionListItemResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}
