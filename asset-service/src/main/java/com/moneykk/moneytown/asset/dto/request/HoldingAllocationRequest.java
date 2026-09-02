package com.moneykk.moneytown.asset.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** 지분 배정 요청 */
public record HoldingAllocationRequest(

        @NotNull(message = "청약 ID는 필수입니다.")
        UUID subscriptionId, // 어떤 청약으로 배정하는지

        @NotNull(message = "자산 ID는 필수입니다.")
        UUID assetId,        // 배정할 자산

        @NotNull(message = "사용자 ID는 필수입니다.")
        UUID userId,         // 지분을 받을 사용자

        @Positive(message = "지분 수량은 1 이상이어야 합니다.")
        long quantity        // 배정할 지분 수량
) {
}
