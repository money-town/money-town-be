package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferingRejectionResponse(
        UUID offeringId,
        OfferingStatus offeringStatus,
        String rejectionReason,
        Instant reviewedAt,
        UUID reviewedBy
) {

    public static OfferingRejectionResponse from(Offering offering) {
        return new OfferingRejectionResponse(
                offering.getOfferingId(),
                offering.getOfferingStatus(),
                offering.getRejectionReason(),
                offering.getReviewedAt(),
                offering.getReviewedBy()
        );
    }
}