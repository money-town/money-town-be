package com.moneykk.moneytown.analysis.ai.query.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;

public record PortfolioSearchCondition(
        RiskType riskType,
        AssetType assetType,
        AiStatus aiStatus
) {
}
