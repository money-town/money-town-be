package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record OfferingReviewRequestResponse(

        @Schema(
                description = "심사를 요청한 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId,

        @Schema(
                description = "심사 요청 후 공모 처리 상태",
                example = "REVIEW_REQUESTED"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "공모 심사를 요청한 시각",
                example = "2026-09-10T09:00:00Z"
        )
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