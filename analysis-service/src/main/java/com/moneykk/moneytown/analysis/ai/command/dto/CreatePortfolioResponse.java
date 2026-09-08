package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CreatePortfolioResponse(
        @Schema(description = "생성된 포트폴리오 ID")
        UUID portfolioId,
        @Schema(description = "포트폴리오 처리 상태 (생성 직후 PROCESSING)", example = "PROCESSING")
        AiStatus status
) {

    public static CreatePortfolioResponse from(Portfolio p){
        return new CreatePortfolioResponse(p.getId(), p.getStatus());
    }
}
