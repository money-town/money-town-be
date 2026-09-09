package com.moneykk.moneytown.analysis.ai.query.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PortfolioDetailResponse(
        @Schema(description = "포트폴리오 ID")
        UUID portfolioId,
        @Schema(description = "처리 상태")
        AiStatus status,
        @Schema(description = "투자 금액(원)", example = "1000000")
        Long investmentAmount,
        @Schema(description = "투자 성향")
        RiskType riskType,
        @Schema(description = "자산 유형 (미지정 시 null)")
        AssetType assetType,
        @Schema(description = "AI 추천 결과 원문 (JSON 문자열, 완료 시에만 존재)")
        String response,
        @Schema(description = "실패 사유 (실패 시에만 존재)")
        String errorMessage,
        @Schema(description = "처리 완료 시각 (미완료 시 null)")
        Instant completedAt
) {
    public static PortfolioDetailResponse from(Portfolio p){
        return new PortfolioDetailResponse(p.getId(), p.getStatus(), p.getInvestmentAmount(), p.getRiskType(),
                p.getAssetType(), p.getResponse(), p.getErrorMessage(), p.getCompletedAt());
    }
}
