package com.moneykk.moneytown.analysis.ai.command.dto;

import java.util.List;
import java.util.UUID;

public record PortfolioRecommendation(
        List<Allocation> allocations,
        String summary,
        String disclaimer
) {
    public record Allocation(
            UUID offeringId,
            String title,
            int percentage,
            long amount,
            String reason
    ){}
}
