package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePortfolioRequest(
    @NotNull
    @Min(value = 10_000, message = "최소 투자 금액은 10,000원입니다.")
    Long investmentAmount,
    @NotNull(message = "리스크 타입은 꼭 입력 해주셔야합니다.")
    RiskType riskType,
    AssetType assetType
) {
}
