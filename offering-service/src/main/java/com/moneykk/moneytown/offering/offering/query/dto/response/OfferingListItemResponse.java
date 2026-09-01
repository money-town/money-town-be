package com.moneykk.moneytown.offering.offering.query.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferingListItemResponse(
        UUID offeringId,
        UUID assetId,
        String title,
        Long pricePerUnit,
        Long totalQuantity,
        Long remainingQuantity,
        OfferingStatus offeringStatus,
        Instant startAt,
        Instant endAt
) {

    public static OfferingListItemResponse from(Offering offering) {
        return new OfferingListItemResponse(
                offering.getOfferingId(),
                offering.getAssetId(),
                offering.getTitle(),
                offering.getPricePerUnit(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getOfferingStatus(),
                offering.getStartAt(),
                offering.getEndAt()
        );
    }
}