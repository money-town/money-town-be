package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MyDividendPayoutListItemResponse(
        @Schema(description = "배당 지급 ID") UUID dividendPayoutId,
        @Schema(description = "자산 ID") UUID assetId,
        @Schema(description = "정산 회차 ID") UUID settlementBatchId,
        @Schema(description = "배당 기준일") LocalDate recordDate,
        @Schema(description = "기준일 보유 지분율") BigDecimal shareRatio,
        @Schema(description = "지급 금액") Long amount,
        @Schema(description = "지급 상태") PayoutStatus status,
        @Schema(description = "지급 완료 시각 (PAID 상태가 아니면 null)") Instant paidAt
) {

    public static MyDividendPayoutListItemResponse of(DividendPayoutRepository.MyDividendPayoutRow row) {
        Instant paidAt = row.getStatus() == PayoutStatus.PAID ? row.getUpdatedAt() : null;
        return new MyDividendPayoutListItemResponse(
                row.getDividendPayoutId(),
                row.getAssetId(),
                row.getSettlementBatchId(),
                row.getRecordDate(),
                row.getShareRatio(),
                row.getAmount(),
                row.getStatus(),
                paidAt
        );
    }
}