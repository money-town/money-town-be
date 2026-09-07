package com.moneykk.moneytown.offering.offering.command.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record OfferingCreateRequest(

        @Schema(
                description = "공모 대상 자산 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @NotNull(message = "대상 자산 ID는 필수입니다.")
        UUID assetId,

        @Schema(
                description = "공모 상품명",
                example = "강남 오피스텔 조각투자 1차 공모"
        )
        @NotBlank(message = "공모 상품명은 필수입니다.")
        @Size(max = 200, message = "공모 상품명은 200자를 초과할 수 없습니다.")
        String title,

        @Schema(
                description = "공모 총 모집 수량",
                example = "10000"
        )
        @NotNull(message = "총 모집 수량은 필수입니다.")
        @Min(value = 1, message = "총 모집 수량은 1 이상이어야 합니다.")
        Long totalQuantity,

        @Schema(
                description = "투자자 1명이 신청할 수 있는 최소 청약 수량",
                example = "10"
        )
        @NotNull(message = "최소 청약 수량은 필수입니다.")
        @Min(value = 1, message = "최소 청약 수량은 1 이상이어야 합니다.")
        Long minSubscriptionQuantity,

        @Schema(
                description = "투자자 1명이 신청할 수 있는 최대 청약 수량",
                example = "1000"
        )
        @NotNull(message = "최대 청약 수량은 필수입니다.")
        @Min(value = 1, message = "최대 청약 수량은 1 이상이어야 합니다.")
        Long maxSubscriptionQuantity,

        @Schema(
                description = "공모 모집 시작 시각",
                example = "2026-09-10T09:00:00Z"
        )
        @NotNull(message = "모집 시작 시각은 필수입니다.")
        Instant startAt,

        @Schema(
                description = "공모 모집 종료 시각",
                example = "2026-09-17T09:00:00Z"
        )
        @NotNull(message = "모집 종료 시각은 필수입니다.")
        Instant endAt
) {
}