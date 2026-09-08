package com.moneykk.moneytown.settlement.command.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record OpenFinalSettlementRequest(

        @Schema(description = "최종 정산(원금반환) 대상 자산 ID")
        @NotNull(message = "자산 ID는 필수입니다.")
        UUID assetId,

        @Schema(description = "자산 종료(해지) 확정 시각. 이 시점 기준 보유자 스냅샷으로 원금을 반환한다.")
        @NotNull(message = "자산 종료 시각은 필수입니다.")
        Instant terminatedAt,

        @Schema(description = "지분 1개당 반환 단가")
        @NotNull(message = "정산 단가는 필수입니다.")
        @Positive(message = "정산 단가는 1 이상이어야 합니다.")
        Long unitPrice

) {
}