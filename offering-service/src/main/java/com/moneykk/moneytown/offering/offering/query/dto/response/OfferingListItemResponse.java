package com.moneykk.moneytown.offering.offering.query.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record OfferingListItemResponse(

        @Schema(
                description = "공모 ID",
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
                example = "강남 오피스텔 조각투자  1차 공모"
        )
        String title,

        @Schema(
                description = "조각당 단위 가격",
                example = "100000"
        )
        Long pricePerUnit,

        @Schema(
                description = "공모 총 모집 수량",
                example = "100000"
        )
        Long totalQuantity,

        @Schema(
                description = "현재 청약 가능한 잔여 수량",
                example = "7600"
        )
        Long remainingQuantity,

        @Schema(
                description = "현재 공모 처리 상태",
                example = "OPEN"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "공모 모집 시작 시각",
                example = "2026-09-10T09:00:00Z"
        )
        Instant startAt,

        @Schema(
                description = "공모 모집 종료 시각",
                example = "2026-09-17T09:00:00Z"
        )
        Instant endAt
) {

    public static OfferingListItemResponse from(Offering offering) {
        return new OfferingListItemResponse(
                offering.getOfferingId(),
                offering.getAssetId(),
                offering.getTitle(),
                offering.getPricePerUnit(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getOfferingStatus(),
                offering.getStartAt(),
                offering.getEndAt()
        );
    }
}