package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

public record SettlementDepositResponse(
        Long transactionId,
        Long walletId,
        String type,
        Long amount,
        UUID finalSettlementBatchId,
        Instant createdAt
) {
}