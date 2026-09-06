package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePortfolioRequest(
    @NotNull @Positive
    Long investmentAmount,
    @NotNull
    RiskType riskType,
    AssetType assetType
) {
}
