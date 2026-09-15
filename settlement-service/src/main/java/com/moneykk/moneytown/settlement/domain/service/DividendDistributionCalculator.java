package com.moneykk.moneytown.settlement.domain.service;

import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// 최대 잔여법: 지분 비율대로 나눈 뒤 floor로 절사하면 총액과의 차액(잔여)이 생긴다.
// 이 잔여를 소수부가 큰 보유자부터 1원씩 배분해, 회차마다 잔여금이 남지 않게 한다.
// ① 소수부 큰 순 → ② 먼저 투자한 순 → ③ 사용자 ID 순
public final class DividendDistributionCalculator {

    private DividendDistributionCalculator() {
    }

    public static Distribution distribute(long totalAmount, long totalHoldingQuantity, List<HoldingItem> holdings) {
        List<Candidate> candidates = new ArrayList<>();
        BigInteger totalAmountBig = BigInteger.valueOf(totalAmount);
        BigInteger totalHoldingQuantityBig = BigInteger.valueOf(totalHoldingQuantity);
        BigDecimal totalHoldingQuantityDecimal = BigDecimal.valueOf(totalHoldingQuantity);

        long flooredSum = 0L;
        for (HoldingItem holding : holdings) {
            Long quantity = holding.quantity();
            if (quantity == null || quantity <= 0) {
                continue;
            }

            BigInteger numerator = totalAmountBig.multiply(BigInteger.valueOf(quantity));
            BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(totalHoldingQuantityBig);
            long flooredAmount = quotientAndRemainder[0].longValueExact();
            BigInteger fractionalRemainder = quotientAndRemainder[1];
            BigDecimal shareRatio = BigDecimal.valueOf(quantity)
                    .divide(totalHoldingQuantityDecimal, 8, RoundingMode.HALF_UP);

            candidates.add(new Candidate(
                    holding.userId(), holding.firstAcquiredAt(), shareRatio, flooredAmount, fractionalRemainder));
            flooredSum += flooredAmount;
        }

        // totalHoldingQuantity가 후보들의 수량 합과 같다면(정상적인 스냅샷이라면 항상 그렇다)
        // 0 <= unitsToDistribute < candidates.size() 가 수학적으로 보장
        long unitsToDistribute = totalAmount - flooredSum;
        candidates.sort(Candidate.LARGEST_REMAINDER_ORDER);
        for (int i = 0; i < candidates.size() && i < unitsToDistribute; i++) {
            candidates.get(i).addOneWon();
        }

        List<PayoutAllocation> allocations = candidates.stream()
                .map(Candidate::toAllocation)
                .toList();
        return new Distribution(allocations);
    }

    public record PayoutAllocation(UUID investorId, BigDecimal shareRatio, Long amount) {
    }

    public record Distribution(List<PayoutAllocation> allocations) {
    }

    private static final class Candidate {

        private static final Comparator<Candidate> LARGEST_REMAINDER_ORDER = Comparator
                .comparing((Candidate c) -> c.fractionalRemainder, Comparator.reverseOrder())
                .thenComparing((Candidate c) -> c.firstAcquiredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.investorId);

        private final UUID investorId;
        private final Instant firstAcquiredAt;
        private final BigDecimal shareRatio;
        private final BigInteger fractionalRemainder;
        private long amount;

        private Candidate(UUID investorId, Instant firstAcquiredAt, BigDecimal shareRatio,
                           long flooredAmount, BigInteger fractionalRemainder) {
            this.investorId = investorId;
            this.firstAcquiredAt = firstAcquiredAt;
            this.shareRatio = shareRatio;
            this.amount = flooredAmount;
            this.fractionalRemainder = fractionalRemainder;
        }

        private void addOneWon() {
            this.amount += 1;
        }

        private PayoutAllocation toAllocation() {
            return new PayoutAllocation(investorId, shareRatio, amount);
        }
    }
}