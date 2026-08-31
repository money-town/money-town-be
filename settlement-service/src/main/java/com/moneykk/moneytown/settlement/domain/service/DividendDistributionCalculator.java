package com.moneykk.moneytown.settlement.domain.service;

import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DividendDistributionCalculator {

    private DividendDistributionCalculator() {
    }

    public static Distribution distribute(long totalAmount, long totalHoldingQuantity, List<HoldingItem> holdings) {
        List<PayoutAllocation> allocations = new ArrayList<>();
        BigInteger totalAmountBig = BigInteger.valueOf(totalAmount);
        BigInteger totalHoldingQuantityBig = BigInteger.valueOf(totalHoldingQuantity);
        BigDecimal totalHoldingQuantityDecimal = BigDecimal.valueOf(totalHoldingQuantity);

        long distributedSum = 0L;
        for (HoldingItem holding : holdings) {
            Long quantity = holding.quantity();
            if (quantity == null || quantity <= 0) {
                continue;
            }

            long amount = totalAmountBig.multiply(BigInteger.valueOf(quantity))
                    .divide(totalHoldingQuantityBig)
                    .longValueExact();
            BigDecimal shareRatio = BigDecimal.valueOf(quantity)
                    .divide(totalHoldingQuantityDecimal, 8, RoundingMode.HALF_UP);

            allocations.add(new PayoutAllocation(holding.userId(), shareRatio, amount));
            distributedSum += amount;
        }

        long remainderAmount = totalAmount - distributedSum;
        return new Distribution(allocations, remainderAmount);
    }

    public record PayoutAllocation(UUID investorId, BigDecimal shareRatio, Long amount) {
    }

    public record Distribution(List<PayoutAllocation> allocations, Long remainderAmount) {
    }
}