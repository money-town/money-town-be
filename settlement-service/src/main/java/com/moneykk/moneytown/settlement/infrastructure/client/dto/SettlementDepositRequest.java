package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.util.UUID;

public record SettlementDepositRequest(
        String idempotencyKey,
        UUID investorId,
        UUID finalSettlementBatchId,
        Long amount
) {
}