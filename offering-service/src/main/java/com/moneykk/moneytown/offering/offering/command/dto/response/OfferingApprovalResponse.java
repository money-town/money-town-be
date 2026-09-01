package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferingApprovalResponse(
        UUID offeringId,
        OfferingStatus offeringStatus,
        Instant reviewedAt,
        UUID reviewedBy
) {

    public static OfferingApprovalResponse from(Offering offering) {
        return new OfferingApprovalResponse(
                offering.getOfferingId(),
                offering.getOfferingStatus(),
                offering.getReviewedAt(),
                offering.getReviewedBy()
        );
    }
}