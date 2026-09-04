package com.moneykk.moneytown.analysis.ai.query.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;

import java.time.Instant;
import java.util.UUID;

public record PortfolioDetailResponse(
        UUID portfolioId,
        AiStatus status,
        Long investmentAmount,
        RiskType riskType,
        AssetType assetType,
        String response,
        String errorMessage,
        Instant completedAt
) {
    public static PortfolioDetailResponse from(Portfolio p){
        return new PortfolioDetailResponse(p.getId(), p.getStatus(), p.getInvestmentAmount(), p.getRiskType(),
                p.getAssetType(), p.getResponse(), p.getErrorMessage(), p.getCompletedAt());
    }
}
