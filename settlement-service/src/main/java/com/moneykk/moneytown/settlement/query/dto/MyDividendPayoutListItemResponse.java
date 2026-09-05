package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MyDividendPayoutListItemResponse(
        UUID dividendPayoutId,
        UUID assetId,
        UUID settlementBatchId,
        LocalDate recordDate,
        BigDecimal shareRatio,
        Long amount,
        PayoutStatus status,
        Instant paidAt
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