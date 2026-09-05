package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.util.UUID;

public record DividendDepositRequest(
        String idempotencyKey,
        UUID investorId,
        UUID settlementBatchId,
        Long amount
) {
}