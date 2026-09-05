package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;

import java.time.Instant;
import java.util.UUID;

// settlement-service의 WalletServiceClient.depositDividend가 기대하는 응답과 동일한 구조
public record DividendDepositResponse(
        Long transactionId,
        Long walletId,
        String type,
        Long amount,
        UUID settlementBatchId,
        Instant createdAt
) {
    public static DividendDepositResponse from(WalletTransaction transaction) {
        return new DividendDepositResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getType().name(),
                transaction.getAmount(),
                UUID.fromString(transaction.getReferenceId()),
                transaction.getCreatedAt()
        );
    }
}
