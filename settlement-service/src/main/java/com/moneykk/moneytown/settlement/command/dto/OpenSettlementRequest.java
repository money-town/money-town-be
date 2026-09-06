package com.moneykk.moneytown.settlement.command.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record OpenSettlementRequest(

        @NotNull(message = "자산 ID는 필수입니다.")
        UUID assetId,

        @NotNull(message = "수익 ID는 필수입니다.")
        UUID revenueId,

        // 배당 기준일. 미지정 시 수익 발생 기간 종료일(periodEnd)로 대체한다.
        LocalDate recordDate

) {
}