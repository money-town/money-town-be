package com.moneykk.moneytown.analysis.ai.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AssetSummary(
        UUID assetId,
        String assetType,
        String assetName,
        AssetStatus assetStatus,
        BigDecimal expectedReturnRate,
        long valuationAmount,
        String description){
}
