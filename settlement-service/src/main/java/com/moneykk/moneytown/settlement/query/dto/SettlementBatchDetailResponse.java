package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementBatchDetailResponse(
        @Schema(description = "정산 회차 ID") UUID settlementBatchId,
        @Schema(description = "자산 ID") UUID assetId,
        @Schema(description = "정산 대상 수익 ID") UUID revenueId,
        @Schema(description = "배당 기준일") LocalDate recordDate,
        @Schema(description = "배당 총액") Long totalAmount,
        @Schema(description = "정산 회차 상태") SettlementStatus status,
        @Schema(description = "회차 생성 시각") Instant createdAt,
        @Schema(description = "마지막 상태 변경 시각") Instant updatedAt,
        @Schema(description = "지급 건수 집계") PayoutSummary payoutSummary
) {

    public static SettlementBatchDetailResponse of(SettlementBatch batch, PayoutSummary payoutSummary) {
        return new SettlementBatchDetailResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getRevenueId(),
                batch.getRecordDate(),
                batch.getTotalAmount(),
                batch.getStatus(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                payoutSummary
        );
    }

    public record PayoutSummary(
            @Schema(description = "전체 지급 건수") long totalCount,
            @Schema(description = "지급 완료(PAID) 건수") long paidCount,
            @Schema(description = "실패(DEAD_LETTER) 건수") long failedCount,
            @Schema(description = "대기중(QUEUED+PROCESSING+RETRYING) 건수") long pendingCount,
            @Schema(description = "관리자가 포기 처리(ABANDONED)한 건수 — 별도 방법으로 지급 완료된 건이며 failedCount에는 포함되지 않는다") long abandonedCount
    ) {
    }
}