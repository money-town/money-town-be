package com.moneykk.moneytown.analysis.ai.query.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;

import java.time.Instant;
import java.util.UUID;

public record PortfolioItemResponse(
        UUID portfolioId,
        Long investmentAmount,
        RiskType riskType,
        AssetType assetType,
        AiStatus status,
        Instant createdAt,
        Instant completedAt
) {

    public static PortfolioItemResponse from(Portfolio p){
        return new PortfolioItemResponse(p.getId(), p.getInvestmentAmount(), p.getRiskType(), p.getAssetType(), p.getStatus(), p.getCompletedAt(), p.getCreatedAt());
    }
}
