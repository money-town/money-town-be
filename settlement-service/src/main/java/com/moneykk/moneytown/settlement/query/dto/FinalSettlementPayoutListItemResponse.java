package com.moneykk.moneytown.settlement.query.dto;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;

import java.util.UUID;

public record FinalSettlementPayoutListItemResponse(
        UUID finalSettlementPayoutId,
        UUID investorId,
        Long quantity,
        Long amount,
        PayoutStatus status,
        Integer retryCount
) {

    public static FinalSettlementPayoutListItemResponse of(FinalSettlementPayout payout) {
        return new FinalSettlementPayoutListItemResponse(
                payout.getId(),
                payout.getInvestorId(),
                payout.getQuantity(),
                payout.getAmount(),
                payout.getStatus(),
                payout.getRetryCount()
        );
    }
}