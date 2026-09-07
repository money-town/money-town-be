package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record FinalSettlementBatchDetailResponse(
        @Schema(description = "최종 정산 회차 ID") UUID finalSettlementBatchId,
        @Schema(description = "자산 ID") UUID assetId,
        @Schema(description = "자산 종료(해지) 확정 시각") Instant terminatedAt,
        @Schema(description = "지분 1개당 반환 단가") Long unitPrice,
        @Schema(description = "원금반환 총액") Long totalAmount,
        @Schema(description = "최종 정산 회차 상태") SettlementStatus status,
        @Schema(description = "반환 건수 집계") Progress progress,
        @Schema(description = "회차 생성 시각") Instant createdAt,
        @Schema(description = "마지막 상태 변경 시각") Instant updatedAt
) {

    public static FinalSettlementBatchDetailResponse of(FinalSettlementBatch batch, Progress progress) {
        return new FinalSettlementBatchDetailResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getTerminatedAt(),
                batch.getUnitPrice(),
                batch.getTotalAmount(),
                batch.getStatus(),
                progress,
                batch.getCreatedAt(),
                batch.getUpdatedAt()
        );
    }

    public record Progress(
            @Schema(description = "전체 반환 건수") long totalCount,
            @Schema(description = "반환 완료(PAID) 건수") long paidCount,
            @Schema(description = "실패(DEAD_LETTER) 건수") long failedCount,
            @Schema(description = "대기중(QUEUED+PROCESSING+RETRYING) 건수") long pendingCount
    ) {
    }
}