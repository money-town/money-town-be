package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HoldingsSnapshotResponse(
        UUID assetId,
        LocalDate asOf,
        Long totalHoldingQuantity,
        List<HoldingItem> items,
        String nextCursor,
        boolean hasNext
) {
}