package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferingCreateResponse(
        UUID offeringId,
        UUID assetId,
        String title,
        OfferingStatus offeringStatus,
        Long totalQuantity,
        Long remainingQuantity,
        Instant createdAt
) {

    public static OfferingCreateResponse from(Offering offering) {
        return new OfferingCreateResponse(
                offering.getOfferingId(),
                offering.getAssetId(),
                offering.getTitle(),
                offering.getOfferingStatus(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getCreatedAt()
        );
    }
}