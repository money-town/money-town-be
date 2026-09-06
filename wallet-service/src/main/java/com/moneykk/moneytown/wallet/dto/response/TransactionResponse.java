package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record TransactionResponse(
        @Schema(description = "거래 ID") Long transactionId,
        @Schema(description = "지갑 ID") Long walletId,
        @Schema(description = "거래 타입") WalletTransactionType type,
        @Schema(description = "거래 금액") long amount,
        @Schema(description = "거래 처리 후 총 잔액") long balanceAfter,
        @Schema(description = "거래 처리 시각") Instant createdAt
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
