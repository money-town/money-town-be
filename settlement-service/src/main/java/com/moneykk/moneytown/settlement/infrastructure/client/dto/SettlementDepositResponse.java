package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.Instant;

public record SettlementDepositResponse(
        Long transactionId,
        Long walletId,
        String type,
        Long amount,
        Instant createdAt,
        String note
) {
}