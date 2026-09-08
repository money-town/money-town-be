package com.moneykk.moneytown.offering.subscription.command.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubscriptionCreateRequest(

        @Schema(
                description = "투자자가 신청한 청약 수량. 공모의 최소·최대 청약 수량 범위 안에 있어야 합니다.",
                example = "10"
        )
        @NotNull(message = "청약 수량은 필수입니다.")
        @Positive(message = "청약 수량은 1 이상이어야 합니다.")
        Long quantity

) {
}