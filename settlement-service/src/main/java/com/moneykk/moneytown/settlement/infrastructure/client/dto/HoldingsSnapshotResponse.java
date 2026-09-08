package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HoldingsSnapshotResponse(
        UUID assetId,
        LocalDate asOf,
        long totalHoldingQuantity,
        List<HoldingItem> holdings,
        UUID nextCursor,
        boolean hasNext
) {
}