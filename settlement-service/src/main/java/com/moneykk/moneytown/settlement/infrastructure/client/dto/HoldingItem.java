package com.moneykk.moneytown.settlement.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

public record HoldingItem(
        UUID holdingId,
        UUID userId,
        Long quantity,
        Instant firstAcquiredAt
) {
}