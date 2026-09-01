package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferingReviewRequestResponse(
        UUID offeringId,
        OfferingStatus offeringStatus,
        Instant reviewRequestedAt
) {

    public static OfferingReviewRequestResponse from(Offering offering) {
        return new OfferingReviewRequestResponse(
                offering.getOfferingId(),
                offering.getOfferingStatus(),
                offering.getReviewRequestedAt()
        );
    }
}