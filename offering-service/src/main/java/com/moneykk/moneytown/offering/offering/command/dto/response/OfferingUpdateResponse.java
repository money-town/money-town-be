package com.moneykk.moneytown.offering.offering.command.dto.response;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record OfferingUpdateResponse(
        @Schema(
                description = "수정된 공모 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID offeringId,

        @Schema(
                description = "수정된 공모 상품명",
                example = "강남 오피스텔 조각투자 1차 공모 변경"
        )
        String title,

        @Schema(
                description = "자산에서 조회하여 공모에 저장한 조각당 단가",
                example = "10000"
        )
        Long pricePerUnit,

        @Schema(
                description = "공모 총 모집 수량",
                example = "12000"
        )
        Long totalQuantity,

        @Schema(
                description = "현재 청약 가능한 잔여 수량",
                example = "12000"
        )
        Long remainingQuantity,

        @Schema(
                description = "투자자 1명이 신청할 수 있는 최소 청약 수량",
                example = "10"
        )
        Long minSubscriptionQuantity,

        @Schema(
                description = "투자자 1명이 신청할 수 있는 최대 청약 수량",
                example = "1000"
        )
        Long maxSubscriptionQuantity,

        @Schema(
                description = "공모 모집 시작 시각",
                example = "2026-09-12T09:00:00Z"
        )
        Instant startAt,

        @Schema(
                description = "공모 모집 종료 시각",
                example = "2026-09-19T09:00:00Z"
        )
        Instant endAt,

        @Schema(
                description = "수정 후 공모 처리 상태",
                example = "DRAFT"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "공모 최종 수정 시각",
                example = "2026-09-10T08:40:00Z"
        )
        Instant updatedAt
) {

    public static OfferingUpdateResponse from(Offering offering) {
        return new OfferingUpdateResponse(
                offering.getOfferingId(),
                offering.getTitle(),
                offering.getPricePerUnit(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getMinSubscriptionQuantity(),
                offering.getMaxSubscriptionQuantity(),
                offering.getStartAt(),
                offering.getEndAt(),
                offering.getOfferingStatus(),
                offering.getUpdatedAt()
        );
    }
}