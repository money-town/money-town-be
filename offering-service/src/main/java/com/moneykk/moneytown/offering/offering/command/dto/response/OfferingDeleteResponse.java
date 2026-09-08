package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record OfferingDeleteResponse(

        @Schema(
                description = "논리 삭제된 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId
) {

    public static OfferingDeleteResponse from(Offering offering) {
        return new OfferingDeleteResponse(
                offering.getOfferingId()
        );
    }
}