package com.moneykk.moneytown.analysis.ai.query.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import io.swagger.v3.oas.annotations.media.Schema;

public record PortfolioSearchCondition(
        @Schema(description = "투자 성향 필터 (선택)")
        RiskType riskType,
        @Schema(description = "자산 유형 필터 (선택)")
        AssetType assetType,
        @Schema(description = "처리 상태 필터 (선택)")
        AiStatus aiStatus
) {
}
