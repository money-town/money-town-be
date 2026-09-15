package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementBatchResponse(
        @Schema(description = "정산 회차 ID") UUID settlementBatchId,
        @Schema(description = "자산 ID") UUID assetId,
        @Schema(description = "정산 대상 수익 ID") UUID revenueId,
        @Schema(description = "배당 기준일") LocalDate recordDate,
        @Schema(description = "배당 총액") Long totalAmount,
        @Schema(description = "정산 회차 상태") SettlementStatus status,
        @Schema(description = "생성된 지급(DividendPayout) 건수") int payoutCount,
        @Schema(description = "회차 생성 시각") Instant createdAt
) {

    public static SettlementBatchResponse of(SettlementBatch batch, int payoutCount) {
        return new SettlementBatchResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getRevenueId(),
                batch.getRecordDate(),
                batch.getTotalAmount(),
                batch.getStatus(),
                payoutCount,
                batch.getCreatedAt()
        );
    }
}