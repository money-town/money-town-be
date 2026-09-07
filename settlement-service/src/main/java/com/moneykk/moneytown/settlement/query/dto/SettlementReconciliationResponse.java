package com.moneykk.moneytown.settlement.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SettlementReconciliationResponse(
        @Schema(description = "정산 회차 ID") UUID settlementBatchId,
        @Schema(description = "기대 배당 총액 (totalAmount - remainderAmount)") long expectedAmount,
        @Schema(description = "지급 내역 전체 합계 (상태 무관, 모든 DividendPayout 합산)") long totalPayoutAmount,
        @Schema(description = "PAID 상태 건만 합산한 참고값") long paidAmount,
        @Schema(description = "expectedAmount와 totalPayoutAmount 일치 여부. false면 데이터 정합성 문제를 의심할 것") boolean reconciled
) {

    public static SettlementReconciliationResponse of(
            UUID settlementBatchId, long expectedAmount, long totalPayoutAmount, long paidAmount) {
        return new SettlementReconciliationResponse(
                settlementBatchId, expectedAmount, totalPayoutAmount, paidAmount, totalPayoutAmount == expectedAmount);
    }
}