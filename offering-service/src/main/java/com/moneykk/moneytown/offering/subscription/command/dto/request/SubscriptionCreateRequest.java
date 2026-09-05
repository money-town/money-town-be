package com.moneykk.moneytown.offering.subscription.command.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubscriptionCreateRequest(

        /**
         * 투자자가 요청한 청약 수량.
         *
         * 공모의 최소·최대 청약 수량 범위 검증은
         * Offering 정보를 조회한 Application 계층에서 수행한다.
         */
        @NotNull(message = "청약 수량은 필수입니다.")
        @Positive(message = "청약 수량은 1 이상이어야 합니다.")
        Long quantity

) {
}