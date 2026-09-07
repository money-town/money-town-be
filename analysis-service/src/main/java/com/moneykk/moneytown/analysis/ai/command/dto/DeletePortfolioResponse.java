package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;

import java.util.UUID;

public record DeletePortfolioResponse(
        UUID portfolioId
) {
    public static DeletePortfolioResponse from(Portfolio p){
        return new DeletePortfolioResponse(p.getId());
    }
}
