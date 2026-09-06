package com.moneykk.moneytown.analysis.ai.command.dto;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;

import java.util.UUID;

public record CreatePortfolioResponse(
        UUID portfolioId,
        AiStatus status
) {

    public static CreatePortfolioResponse from(Portfolio p){
        return new CreatePortfolioResponse(p.getId(), p.getStatus());
    }
}
