package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record DeletePortfolioResponse(
        @Schema(description = "삭제된 포트폴리오 ID")
        UUID portfolioId
) {
    public static DeletePortfolioResponse from(Portfolio p){
        return new DeletePortfolioResponse(p.getId());
    }
}
