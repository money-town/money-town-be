package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.OwnerBurdenPaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 기준일의 수납 전 견적. 수납 완료 여부나 실제 미납 잔액을 의미하지 않는다. */
public record OwnerBurdenQuoteResponse(
        UUID assetId,
        UUID ownerId,
        UUID offeringId,
        OwnerBurdenPaymentMethod paymentMethod,
        Instant offeringCompletedAt,
        LocalDate asOf,
        long principalAmount,
        BigDecimal interestAmount,
        BigDecimal totalAmount
) {
}
