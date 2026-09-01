package com.moneykk.moneytown.settlement.domain.service;

import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.*;

class DividendDistributionCalculatorTest {

    @Test
    @DisplayName("절사 후 남는 금액은 remainderAmount로 반환한다")
    void flooredAmountsSumWithRemainderCarriedOut() {
        UUID investor1 = UUID.randomUUID();
        UUID investor2 = UUID.randomUUID();
        // 총액 100, 분모 3, 보유수량 1/2 -> floor(100*1/3)=33, floor(100*2/3)=66, 합계 99, 잔여 1
        List<HoldingItem> holdings = List.of(
                new HoldingItem(UUID.randomUUID(), investor1, 1L),
                new HoldingItem(UUID.randomUUID(), investor2, 2L)
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 3L, holdings);

        assertThat(distribution.remainderAmount()).isEqualTo(1L);
        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId,
                        DividendDistributionCalculator.PayoutAllocation::amount)
                .containsExactlyInAnyOrder(
                        tuple(investor1, 33L),
                        tuple(investor2, 66L)
                );
    }

    @Test
    @DisplayName("나누어 떨어지면 잔여금은 0이다")
    void exactDivisionLeavesNoRemainder() {
        UUID investor = UUID.randomUUID();
        List<HoldingItem> holdings = List.of(new HoldingItem(UUID.randomUUID(), investor, 100L));

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 100L, holdings);

        assertThat(distribution.remainderAmount()).isZero();
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
                new HoldingItem(UUID.randomUUID(), zeroHolder, 0L),
                new HoldingItem(UUID.randomUUID(), nullHolder, null),
                new HoldingItem(UUID.randomUUID(), validHolder, 10L)
        );

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 10L, holdings);

        assertThat(distribution.allocations())
                .extracting(DividendDistributionCalculator.PayoutAllocation::investorId)
                .containsExactly(validHolder);
    }

    @Test
    @DisplayName("홀딩이 없으면 전액이 잔여금으로 남는다")
    void emptyHoldingsCarriesOutTheWholeAmount() {
        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 10L, List.of());

        assertThat(distribution.allocations()).isEmpty();
        assertThat(distribution.remainderAmount()).isEqualTo(100L);
    }

    @Test
    @DisplayName("지분율은 소수점 8자리로 반올림해서 기록한다")
    void computesShareRatioAtEightDecimalPlaces() {
        UUID investor = UUID.randomUUID();
        List<HoldingItem> holdings = List.of(new HoldingItem(UUID.randomUUID(), investor, 1L));

        DividendDistributionCalculator.Distribution distribution =
                DividendDistributionCalculator.distribute(100L, 3L, holdings);

        assertThat(distribution.allocations().get(0).shareRatio())
                .isEqualByComparingTo(new BigDecimal("0.33333333"));
    }
}