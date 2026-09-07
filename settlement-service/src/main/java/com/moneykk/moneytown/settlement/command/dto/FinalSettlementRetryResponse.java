package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FinalSettlementRetryResponse(
        @Schema(description = "최종 정산 회차 ID") UUID finalSettlementBatchId,
        @Schema(description = "이번 요청으로 재처리(QUEUED로 전환)된 건수") int retriedCount,
        @Schema(description = "재처리 후 회차 상태 (DISBURSING으로 전환됨)") SettlementStatus status
) {

    public static FinalSettlementRetryResponse of(FinalSettlementBatch batch, int retriedCount) {
        return new FinalSettlementRetryResponse(batch.getId(), retriedCount, batch.getStatus());
    }
}