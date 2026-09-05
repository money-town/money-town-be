package com.moneykk.moneytown.asset.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 관리자 지분 수량 조정 요청 */
public record HoldingAdjustmentRequest(

        @NotNull(message = "조정 후 지분 수량은 필수입니다.")
        @PositiveOrZero(message = "조정 후 지분 수량은 0 이상이어야 합니다.")
        Long targetQuantity,

        @NotBlank(message = "지분 조정 사유는 필수입니다.")
        @Size(max = 500, message = "지분 조정 사유는 500자 이하여야 합니다.")
        String reason,

        @NotBlank(message = "멱등성 키는 필수입니다.")
        @Size(max = 100, message = "멱등성 키는 100자 이하여야 합니다.")
        String idempotencyKey
) {
}
