package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;

import java.time.Instant;
import java.util.UUID;

// settlement-service의 WalletServiceClient.depositSettlement가 기대하는 응답과 동일한 구조
public record SettlementDepositResponse(
        Long transactionId,
        Long walletId,
        String type,
        Long amount,
        UUID finalSettlementBatchId,
        Instant createdAt
) {
    public static SettlementDepositResponse from(WalletTransaction transaction) {
        return new SettlementDepositResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getType().name(),
                transaction.getAmount(),
                UUID.fromString(transaction.getReferenceId()),
                transaction.getCreatedAt()
        );
    }
}
