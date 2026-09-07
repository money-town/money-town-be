package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FinalSettlementBatchResponse(
        @Schema(description = "최종 정산 회차 ID") UUID finalSettlementBatchId,
        @Schema(description = "자산 ID") UUID assetId,
        @Schema(description = "원금반환 총액 (보유 수량 × 단가 합계)") Long totalAmount,
        @Schema(description = "최종 정산 회차 상태") SettlementStatus status,
        @Schema(description = "이번 요청으로 새로 생성된 회차인지 여부. false면 이미 존재하던 회차를 그대로 반환한 것(멱등 재요청)이라 지급을 다시 시작하지 않는다.")
        boolean newlyCreated
) {

    public static FinalSettlementBatchResponse of(FinalSettlementBatch batch, boolean newlyCreated) {
        return new FinalSettlementBatchResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getTotalAmount(),
                batch.getStatus(),
                newlyCreated
        );
    }
}