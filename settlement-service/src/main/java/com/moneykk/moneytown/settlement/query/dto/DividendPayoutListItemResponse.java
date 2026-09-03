package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record DividendPayoutListItemResponse(
        UUID dividendPayoutId,
        UUID investorId,
        BigDecimal shareRatio,
        Long amount,
        PayoutStatus status,
        Integer retryCount
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