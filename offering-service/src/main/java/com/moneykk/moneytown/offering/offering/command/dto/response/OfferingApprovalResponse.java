package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record OfferingApprovalResponse(

        @Schema(
                description = "승인된 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId,

        @Schema(
                description = "승인 후 공모 처리 상태",
                example = "SCHEDULED"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "공모 심사 완료 시각",
                example = "2026-09-10T10:00:00Z"
        )
        Instant reviewedAt,

        @Schema(
                description = "공모를 승인한 관리자 ID",
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        )
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