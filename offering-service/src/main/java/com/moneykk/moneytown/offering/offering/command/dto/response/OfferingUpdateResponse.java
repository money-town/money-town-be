package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferingUpdateResponse(
        UUID offeringId,
        String title,
        BigDecimal pricePerUnit,
        Long totalQuantity,
        Long remainingQuantity,
        Long minSubscriptionQuantity,
        Long maxSubscriptionQuantity,
        Instant startAt,
        Instant endAt,
        OfferingStatus offeringStatus,
        Instant updatedAt
) {

    public static OfferingUpdateResponse from(Offering offering) {
        return new OfferingUpdateResponse(
                offering.getOfferingId(),
                offering.getTitle(),
                offering.getPricePerUnit(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getMinSubscriptionQuantity(),
                offering.getMaxSubscriptionQuantity(),
                offering.getStartAt(),
                offering.getEndAt(),
                offering.getOfferingStatus(),
                offering.getUpdatedAt()
        );
    }
}