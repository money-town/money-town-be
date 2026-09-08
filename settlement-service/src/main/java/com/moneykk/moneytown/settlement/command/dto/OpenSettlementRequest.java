package com.moneykk.moneytown.settlement.command.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record OpenSettlementRequest(

        @Schema(description = "정산 회차를 개시할 자산 ID")
        @NotNull(message = "자산 ID는 필수입니다.")
        UUID assetId,

        @Schema(description = "정산 대상 수익 ID (READY 상태여야 함)")
        @NotNull(message = "수익 ID는 필수입니다.")
        UUID revenueId,

        @Schema(description = "배당 기준일. 미지정 시 수익 발생 기간 종료일(periodEnd)로 대체한다.")
        LocalDate recordDate

) {
}