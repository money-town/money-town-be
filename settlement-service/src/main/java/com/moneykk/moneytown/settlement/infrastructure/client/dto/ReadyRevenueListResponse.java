package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.util.List;
import java.util.UUID;

public record ReadyRevenueListResponse(
        List<RevenueResponse> revenues,
        UUID nextCursor,
        boolean hasNext
) {
}