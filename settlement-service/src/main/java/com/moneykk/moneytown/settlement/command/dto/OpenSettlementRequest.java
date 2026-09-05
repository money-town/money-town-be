package com.moneykk.moneytown.settlement.command.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenSettlementRequest(

        @NotNull(message = "자산 ID는 필수입니다.")
        UUID assetId,

        @NotNull(message = "수익 ID는 필수입니다.")
        UUID revenueId

) {
}