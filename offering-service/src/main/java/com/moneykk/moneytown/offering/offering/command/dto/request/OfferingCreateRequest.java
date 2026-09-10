package com.moneykk.moneytown.offering.offering.command.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record OfferingCreateRequest(

        @Schema(
                description = "공모 대상 자산 ID",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @NotNull(message = "대상 자산 ID는 필수입니다.")
        UUID assetId,

        // title 필드 - Asset Service에서 받은 자산명으로 자동 생성합니다.

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
                description = """
                공모 모집 시작 시각입니다.
                한국 시각 기준으로 yyyy-MM-dd HH:mm 형식으로 입력합니다.
                현재 시각보다 충분히 미래로 설정해야 합니다.
                """,
                type = "string",
                example = "2030-01-01 14:40"
        )
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        @NotNull(message = "모집 시작 시각은 필수입니다.")
        LocalDateTime startAt,

        @Schema(
                description = """
                공모 모집 종료 시각입니다.
                한국 시각 기준으로 yyyy-MM-dd HH:mm 형식으로 입력하며
                모집 시작 시각보다 이후여야 합니다.
                """,
                type = "string",
                example = "2030-01-08 18:00"
        )
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        @NotNull(message = "모집 종료 시각은 필수입니다.")
        LocalDateTime endAt
) {
}