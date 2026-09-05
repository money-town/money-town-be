package com.moneykk.moneytown.wallet.dto.response;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record TransactionListItemResponse(
        @Schema(description = "거래 ID") Long transactionId,
        @Schema(description = "거래 타입") WalletTransactionType type,
        @Schema(description = "거래 금액") long amount,
        @Schema(description = "거래 처리 전 총 잔액") long balanceBefore,
        @Schema(description = "거래 처리 후 총 잔액") long balanceAfter,
        @Schema(description = "타입별 참조 ID (예: HOLD/DEDUCT/REFUND는 subscriptionId, DIVIDEND는 settlementBatchId). DEPOSIT/WITHDRAW는 null")
        String referenceId,
        @Schema(description = "거래 처리 시각") Instant createdAt
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
