package com.moneykk.moneytown.asset.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** 지분 배정 요청 */
public record HoldingAllocationRequest(

        @NotNull
        UUID subscriptionId, // 어떤 청약으로 배정하는지

        @NotNull
        UUID assetId,        // 배정할 자산

        @NotNull
        UUID userId,         // 지분을 받을 사용자

        @Positive
        long quantity        // 배정할 지분 수량
) {
}