package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FinalSettlementBatchResponse(
        @Schema(description = "최종 정산 회차 ID") UUID finalSettlementBatchId,
        @Schema(description = "자산 ID") UUID assetId,
        @Schema(description = "원금반환 총액 (보유 수량 × 단가 합계)") Long totalAmount,
        @Schema(description = "최종 정산 회차 상태") SettlementStatus status
) {

    public static FinalSettlementBatchResponse of(FinalSettlementBatch batch) {
        return new FinalSettlementBatchResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getTotalAmount(),
                batch.getStatus()
        );
    }
}