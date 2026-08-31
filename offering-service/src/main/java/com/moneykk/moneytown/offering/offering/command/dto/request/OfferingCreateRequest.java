package com.moneykk.moneytown.offering.offering.command.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferingCreateRequest(

        @NotNull(message = "대상 자산 ID는 필수입니다.")
        UUID assetId,

        @NotBlank(message = "공모 상품명은 필수입니다.")
        @Size(max = 200, message = "공모 상품명은 200자를 초과할 수 없습니다.")
        String title,

        @NotNull(message = "단위당 청약 가격은 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false,
                message = "단위당 청약 가격은 0보다 커야 합니다.")
        BigDecimal pricePerUnit,

        @NotNull(message = "총 발행 수량은 필수입니다.")
        @Min(value = 1, message = "총 발행 수량은 1 이상이어야 합니다.")
        Long totalQuantity,

        @NotNull(message = "최소 청약 수량은 필수입니다.")
        @Min(value = 1, message = "최소 청약 수량은 1 이상이어야 합니다.")
        Long minSubscriptionQuantity,

        @NotNull(message = "최대 청약 수량은 필수입니다.")
        @Min(value = 1, message = "최대 청약 수량은 1 이상이어야 합니다.")
        Long maxSubscriptionQuantity,

        @NotNull(message = "모집 시작 시각은 필수입니다.")
        Instant startAt,

        @NotNull(message = "모집 종료 시각은 필수입니다.")
        Instant endAt
) {
}