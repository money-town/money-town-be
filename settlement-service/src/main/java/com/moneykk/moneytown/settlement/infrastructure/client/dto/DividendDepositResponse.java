package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

public record DividendDepositResponse(
        Long transactionId,
        Long walletId,
        String type,
        Long amount,
        UUID settlementBatchId,
        Instant createdAt
) {
}