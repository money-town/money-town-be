package com.moneykk.moneytown.offering.offering.query.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moneykk.moneytown.offering.offering.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferingDetailResponse(

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
                description = "공모를 등록한 발행자 ID. 공모 소유자 또는 관리자 조회 시에만 포함됩니다.",
                example = "7c9e6679-7425-40de-944b-e07fc1ball90ae7"
        )
        UUID issuerId,

        @Schema(
                description = "공모 상품명",
                example = "강남 오피스텔 조각투자 1차 공모"
        )
        String title,

        @Schema(
                description = "조각당 단위 가격",
                example = "100000"
        )
        Long pricePerUnit,

        @Schema(
                description = "공모 총 모집 수량",
                example = "10000"
        )
        Long totalQuantity,

        @Schema(
                description = "현재 청약 가능한 잔여 수량",
                example = "7600"
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
                example = "2026-09-10T09:00:00Z"
        )
        Instant startAt,

        @Schema(
                description = "공모 모집 종료 시각",
                example = "2026-09-17T09:00:00Z"
        )
        Instant endAt,

        @Schema(
                description = "현재 공모 처리 상태",
                example = "OPEN"
        )
        OfferingStatus offeringStatus,

        @Schema(
                description = "공모 취소 사유 유형. 취소된 공모를 소유자 또는 관리자가 조회할 때 포함됩니다.",
                example = "ADMIN_CANCELLED"
        )
        CancellationType cancellationType,

        @Schema(
                description = "공모 생성 시각",
                example = "2026-09-08T08:30:00Z"
        )
        Instant createdAt,

        @Schema(
                description = "공모 최종 수정 시각",
                example = "2026-09-10T08:00:00Z"
        )
        Instant updatedAt
) {

    // TODO: 응답 조합 로직이 복잡해질 경우 Builder 패턴 적용 검토
    public static OfferingDetailResponse from(
            Offering offering,
            boolean includePrivateFields
    ) {
        return new OfferingDetailResponse(
                offering.getOfferingId(),
                offering.getAssetId(),
                includePrivateFields ? offering.getIssuerId() : null,
                offering.getTitle(),
                offering.getPricePerUnit(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getMinSubscriptionQuantity(),
                offering.getMaxSubscriptionQuantity(),
                offering.getStartAt(),
                offering.getEndAt(),
                offering.getOfferingStatus(),
                includePrivateFields ? offering.getCancellationType() : null,
                offering.getCreatedAt(),
                offering.getUpdatedAt()
        );
    }
}