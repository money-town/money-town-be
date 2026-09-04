package com.moneykk.moneytown.settlement.command.dto;

import java.util.UUID;

public record OpenSettlementRequest(
        UUID assetId,
        UUID revenueId
) {
}