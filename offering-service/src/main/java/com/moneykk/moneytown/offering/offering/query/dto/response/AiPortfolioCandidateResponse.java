package com.moneykk.moneytown.offering.offering.query.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AiPortfolioCandidateResponse(

        @Schema(description = "공모 ID")
        UUID offeringId,

        @Schema(description = "공모 대상 자산 ID")
        UUID assetId,

        @Schema(description = "공모 상품명")
        String title,

        @Schema(description = "조각당 단위 가격")
        Long pricePerUnit,

        @Schema(description = "공모 총 모집 수량")
        Long totalQuantity,

        @Schema(description = "현재 청약 가능한 잔여 수량")
        Long remainingQuantity,

        @Schema(description = "공모 모집 시작 시각")
        Instant startAt,

        @Schema(description = "공모 모집 종료 시각")
        Instant endAt
) {
}