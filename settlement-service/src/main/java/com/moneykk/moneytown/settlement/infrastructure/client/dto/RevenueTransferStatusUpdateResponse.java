package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

public record RevenueTransferStatusUpdateResponse(
        UUID revenueId,
        RevenueTransferStatus transferStatus,
        Instant transferredAt,
        String failureReason
) {
}