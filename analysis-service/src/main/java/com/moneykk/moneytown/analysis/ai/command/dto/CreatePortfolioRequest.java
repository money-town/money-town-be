package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePortfolioRequest(
    @Schema(description = "투자 금액(원). 최소 10,000원", example = "1000000")
    @NotNull
    @Min(value = 10_000, message = "최소 투자 금액은 10,000원입니다.")
    Long investmentAmount,
    @Schema(description = "투자 성향 (필수)", example = "MEDIUM")
    @NotNull(message = "리스크 타입은 꼭 입력 해주셔야합니다.")
    RiskType riskType,
    @Schema(description = "선호 자산 유형 (선택, 미지정 시 전체)", example = "REAL_ESTATE")
    AssetType assetType
) {
}
