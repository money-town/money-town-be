package com.moneykk.moneytown.offering.offering.command.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferingCreateResponse(
        @Schema(
                description = "생성된 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId,

        @Schema(
                description = "공모 대상 자산 ID",
                example = "8f14e45f-ea4d-4f8b-9d5a-7b30c8d91a21"
        )
        UUID assetId,

        @Schema(
                description = "공모 상품명",
                example = "강남 오피스텔 조각투자 1차 공모"
        )
        String title,

        @Schema(
                description = "생성된 공모의 처리 상태",
                example = "DRAFT"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "공모 총 모집 수량",
                example = "10000"
        )
        Long totalQuantity,

        @Schema(
                description = "현재 청약 가능한 잔여 수량. 공모 생성 시에는 총 모집 수량과 같습니다.",
                example = "10000"
        )
        Long remainingQuantity,

        @Schema(
                description = "공모 생성 시각",
                example = "2026-09-10T08:30:00Z"
        )
        Instant createdAt
) {

    public static OfferingCreateResponse from(Offering offering) {
        return new OfferingCreateResponse(
                offering.getOfferingId(),
                offering.getAssetId(),
                offering.getTitle(),
                offering.getOfferingStatus(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getCreatedAt()
        );
    }
}