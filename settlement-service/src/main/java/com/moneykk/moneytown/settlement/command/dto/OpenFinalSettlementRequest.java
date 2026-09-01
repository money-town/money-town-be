package com.moneykk.moneytown.settlement.command.dto;

import java.time.Instant;
import java.util.UUID;

public record OpenFinalSettlementRequest(
        UUID assetId,
        Instant terminatedAt,
        Long unitPrice
) {
}