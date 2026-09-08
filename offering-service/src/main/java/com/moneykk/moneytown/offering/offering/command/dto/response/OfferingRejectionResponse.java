package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record OfferingRejectionResponse(

        @Schema(
                description = "반려된 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId,

        @Schema(
                description = "반려 후 공모 처리 상태",
                example = "REJECTED"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "관리자가 입력한 공모 반려 사유",
                example = "자산 증빙 자료가 충분하지 않아 공모를 승인할 수 없습니다."
        )
        String rejectionReason,

        @Schema(
                description = "공모 심사 완료 시각",
                example = "2026-09-10T10:00:00Z"
        )
        Instant reviewedAt,

        @Schema(
                description = "공모를 반려한 관리자 ID",
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        )
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