package com.moneykk.moneytown.offering.offering.infrastructure.client.dto;

import java.util.UUID;

public record AssetOfferingInfoResponse(
        UUID assetId,
        UUID userId,
        String assetType,
        String assetName,
        Long unitPrice,
        Long totalShareQuantity,
        Long allocatedQuantity,
        String assetStatus
) {
}