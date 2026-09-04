package com.moneykk.moneytown.settlement.command.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record OpenFinalSettlementRequest(

        @NotNull(message = "자산 ID는 필수입니다.")
        UUID assetId,

        @NotNull(message = "자산 종료 시각은 필수입니다.")
        Instant terminatedAt,

        @NotNull(message = "정산 단가는 필수입니다.")
        @Positive(message = "정산 단가는 1 이상이어야 합니다.")
        Long unitPrice

) {
}