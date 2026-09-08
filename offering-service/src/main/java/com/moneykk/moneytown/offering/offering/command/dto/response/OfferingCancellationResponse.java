package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.util.UUID;

public record OfferingCancellationResponse(
        UUID offeringId,
        OfferingStatus offeringStatus
) {

    public static OfferingCancellationResponse from(
            Offering offering
    ) {
        return new OfferingCancellationResponse(
                offering.getOfferingId(),
                offering.getOfferingStatus()
        );
    }
}