package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record OfferingCancellationResponse(

        @Schema(
                description = "관리자가 중단한 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId,

        @Schema(
                description = "중단 처리 후 공모 상태. 즉시 취소되면 CANCELLED, 비동기 보상이 필요하면 CANCELLING입니다.",
                example = "CANCELLING"
        )
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