package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;

import java.util.UUID;

public record OfferingDeleteResponse(
        UUID offeringId
) {

    public static OfferingDeleteResponse from(Offering offering) {
        return new OfferingDeleteResponse(
                offering.getOfferingId()
        );
    }
}