package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record DividendPayoutListItemResponse(
        @Schema(description = "배당 지급 ID") UUID dividendPayoutId,
        @Schema(description = "투자자(사용자) ID") UUID investorId,
        @Schema(description = "기준일 보유 지분율") BigDecimal shareRatio,
        @Schema(description = "지급 금액") Long amount,
        @Schema(description = "지급 상태") PayoutStatus status,
        @Schema(description = "재시도 횟수") Integer retryCount
) {

    public static DividendPayoutListItemResponse of(DividendPayout payout) {
        return new DividendPayoutListItemResponse(
                payout.getId(),
                payout.getInvestorId(),
                payout.getShareRatio(),
                payout.getAmount(),
                payout.getStatus(),
                payout.getRetryCount()
        );
    }
}