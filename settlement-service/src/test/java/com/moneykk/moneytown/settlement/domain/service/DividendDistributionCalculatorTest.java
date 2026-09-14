package com.moneykk.moneytown.settlement.domain.service;

import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.*;

class DividendDistributionCalculatorTest {

    @Test
    @DisplayName("절사 후 남는 금액은 소수부(잃은 몫)가 더 큰 보유자에게 1원 배분된다")
    void distributesLeftoverWonToLargestFractionalRemainder() {
        UUID investor1 = UUID.randomUUID();
        UUID investor2 = UUID.randomUUID();
        // 총액 100, 분모 3, 보유수량 1/2 -> 정확한 몫은 33.33 / 66.67.
        // floor(100*1/3)=33, floor(100*2/3)=66, 합계 99, 남은 1원은 소수부가 더 큰 investor2가 받는다.
        List<HoldingItem> holdings = List.of(
                new HoldingItem(UUID.randomUUID(), investor1, 1L, null),
                new HoldingItem(UUID.randomUUID(), investor2, 2L, null)
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 3L, holdings);

        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId,
                        DividendDistributionCalculator.PayoutAllocation::amount)
                .containsExactlyInAnyOrder(
                        tuple(investor1, 33L),
                        tuple(investor2, 67L)
                );
    }

    @Test
    @DisplayName("나누어 떨어지면 절사 자체가 없어 추가 배분도 없다")
    void exactDivisionNeedsNoLeftoverDistribution() {
        UUID investor = UUID.randomUUID();
        List<HoldingItem> holdings = List.of(new HoldingItem(UUID.randomUUID(), investor, 100L, null));

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 100L, holdings);

        assertThat(distribution.allocations()).hasSize(1);
        assertThat(distribution.allocations().get(0).amount()).isEqualTo(100L);
        assertThat(distribution.allocations().get(0).shareRatio()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("보유수량이 0이거나 null인 홀딩은 분배 대상에서 제외한다")
    void skipsHoldingsWithNoQuantity() {
        UUID zeroHolder = UUID.randomUUID();
        UUID nullHolder = UUID.randomUUID();
        UUID validHolder = UUID.randomUUID();
        List<HoldingItem> holdings = List.of(
                new HoldingItem(UUID.randomUUID(), zeroHolder, 0L, null),
                new HoldingItem(UUID.randomUUID(), nullHolder, null, null),
                new HoldingItem(UUID.randomUUID(), validHolder, 10L, null)
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 10L, holdings);

        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId)
                .containsExactly(validHolder);
    }

    @Test
    @DisplayName("홀딩이 없으면 배분 결과도 비어있다")
    void emptyHoldingsProduceNoAllocations() {
        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 10L, List.of());

        assertThat(distribution.allocations()).isEmpty();
    }

    @Test
    @DisplayName("지분율은 소수점 8자리로 반올림해서 기록한다")
    void computesShareRatioAtEightDecimalPlaces() {
        UUID investor = UUID.randomUUID();
        List<HoldingItem> holdings = List.of(new HoldingItem(UUID.randomUUID(), investor, 1L, null));

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 3L, holdings);

        assertThat(distribution.allocations().get(0).shareRatio())
                .isEqualByComparingTo(new BigDecimal("0.33333333"));
    }

    @Test
    @DisplayName("소수부가 동률이면 먼저 투자한 사람(firstAcquiredAt이 빠른 사람)이 우선 배분받는다")
    void breaksTiesByFirstAcquiredAt() {
        UUID earliestInvestor = UUID.randomUUID();
        UUID middleInvestor = UUID.randomUUID();
        UUID latestInvestor = UUID.randomUUID();
        // 총액 10, 분모 3, 각자 1주씩 -> 정확한 몫이 모두 3.33...으로 소수부가 완전히 동률.
        // 남는 1원은 가장 먼저 투자한 earliestInvestor가 받는다.
        List<HoldingItem> holdings = List.of(
                new HoldingItem(UUID.randomUUID(), middleInvestor, 1L, Instant.parse("2026-02-01T00:00:00Z")),
                new HoldingItem(UUID.randomUUID(), earliestInvestor, 1L, Instant.parse("2026-01-01T00:00:00Z")),
                new HoldingItem(UUID.randomUUID(), latestInvestor, 1L, Instant.parse("2026-03-01T00:00:00Z"))
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(10L, 3L, holdings);

        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId,
                        DividendDistributionCalculator.PayoutAllocation::amount)
                .containsExactlyInAnyOrder(
                        tuple(earliestInvestor, 4L),
                        tuple(middleInvestor, 3L),
                        tuple(latestInvestor, 3L)
                );
    }

    @Test
    @DisplayName("소수부와 투자 시점까지 동률이면 사용자 ID가 작은 사람이 최후 안전장치로 우선 배분받는다")
    void breaksRemainingTiesByInvestorId() {
        UUID lowestId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID midId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID highestId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<HoldingItem> holdings = List.of(
                new HoldingItem(UUID.randomUUID(), highestId, 1L, null),
                new HoldingItem(UUID.randomUUID(), lowestId, 1L, null),
                new HoldingItem(UUID.randomUUID(), midId, 1L, null)
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(10L, 3L, holdings);

        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId,
                        DividendDistributionCalculator.PayoutAllocation::amount)
                .containsExactlyInAnyOrder(
                        tuple(lowestId, 4L),
                        tuple(midId, 3L),
                        tuple(highestId, 3L)
                );
    }

    @Test
    @DisplayName("절사 손실이 여러 명에게 누적되면 소수부가 큰 순서대로 leftover 전원에게 배분된다")
    void distributesMultipleLeftoverUnitsByDescendingRemainder() {
        UUID investor1 = UUID.randomUUID();
        UUID investor2 = UUID.randomUUID();
        UUID investor3 = UUID.randomUUID();
        // 총액 10, 분모 7, 수량 3/2/2 -> 정확한 몫 4.2857/2.8571/2.8571, floor 4/2/2, 합계 8, leftover 2.
        // investor2/investor3의 소수부(6/7)가 investor1(2/7)보다 커서 둘 다 +1원.
        List<HoldingItem> holdings = List.of(
                new HoldingItem(UUID.randomUUID(), investor1, 3L, Instant.parse("2026-01-01T00:00:00Z")),
                new HoldingItem(UUID.randomUUID(), investor2, 2L, Instant.parse("2026-01-01T00:00:00Z")),
                new HoldingItem(UUID.randomUUID(), investor3, 2L, Instant.parse("2026-01-02T00:00:00Z"))
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(10L, 7L, holdings);

        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId,
                        DividendDistributionCalculator.PayoutAllocation::amount)
                .containsExactlyInAnyOrder(
                        tuple(investor1, 4L),
                        tuple(investor2, 3L),
                        tuple(investor3, 3L)
                );
    }
}