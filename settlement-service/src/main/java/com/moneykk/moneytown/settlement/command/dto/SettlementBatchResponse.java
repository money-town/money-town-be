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
        @Schema(description = "회차 생성 시각") Instant createdAt,
        @Schema(description = "이번 호출로 새로 생성된 회차인지 여부. false면 이미 존재하던 회차를 그대로 반환한 것(멱등 재요청/재시도). "
                + "게이트 용도가 아니라 관측 전용 — 호출자(RevenuePollingScheduler, RevenueReadyEventConsumer)는 이 값과 무관하게 "
                + "notifyTransferred·disburseAsync 후속 처리를 매번 그대로 호출한다.")
        boolean newlyCreated
) {

    public static SettlementBatchResponse of(SettlementBatch batch, int payoutCount, boolean newlyCreated) {
        return new SettlementBatchResponse(
                batch.getId(),
                batch.getAssetId(),
                batch.getRevenueId(),
                batch.getRecordDate(),
                batch.getTotalAmount(),
                batch.getStatus(),
                payoutCount,
                batch.getCreatedAt(),
                newlyCreated
        );
    }
}