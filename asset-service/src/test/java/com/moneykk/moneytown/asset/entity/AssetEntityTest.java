package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetEntityTest {

    @Test
    void assetCannotAllocateMoreThanRemainingShares() {
        Asset asset = new Asset(
                UUID.randomUUID(), "테스트 부동산", AssetType.REAL_ESTATE, "테스트 자산",
                100_000_000L, BigDecimal.valueOf(5), Map.of(), 100L
        );
        ReflectionTestUtils.setField(asset, "status", AssetStatus.APPROVED);

        asset.allocateShares(80);

        assertEquals(80, asset.getAllocatedQuantity());
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> asset.allocateShares(21)
        );
        assertEquals(AssetErrorCode.SHARE_QUANTITY_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void detailAppraisalAmountMatchesValuationAmount() {
        Asset asset = new Asset(
                UUID.randomUUID(), "테스트 부동산", AssetType.REAL_ESTATE, "테스트 자산",
                100_000_000L, BigDecimal.valueOf(5),
                Map.of("appraisalAmount", 120_000_000L), 100L
        );

        assertEquals(100_000_000L, asset.getDetailData().get("appraisalAmount"));
    }

    @Test
    void holdingQuantityCannotBecomeNegative() {
        Holding holding = new Holding(UUID.randomUUID(), UUID.randomUUID(), 10);

        holding.allocate(5);
        holding.revoke(3);

        assertEquals(12, holding.getQuantity());
        BusinessException exception = assertThrows(BusinessException.class, () -> holding.revoke(13));
        assertEquals(AssetErrorCode.INSUFFICIENT_HOLDING_QUANTITY, exception.getErrorCode());
    }

    @Test
    void allocationHistoryRequiresSubscriptionId() {
        BusinessException exception = assertThrows(BusinessException.class, () -> new HoldingHistory(
                UUID.randomUUID(), null, HoldingHistoryType.ALLOCATE,
                10, 0, 10, "allocate-1", null));
        assertEquals(AssetErrorCode.SUBSCRIPTION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void revenueTransferStateStaysConsistent() {
        Revenue revenue = new Revenue(
                UUID.randomUUID(), UUID.randomUUID(), RevenueSourceType.PROPERTY_MANAGER,
                "source-1", RevenueType.RENTAL_INCOME,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                "KRW", LocalDate.now(), LocalDate.now(), Map.of());

        revenue.markFailed("timeout");
        assertEquals(RevenueTransferStatus.FAILED, revenue.getTransferStatus());

        revenue.retry();
        assertEquals(RevenueTransferStatus.READY, revenue.getTransferStatus());
    }

    @Test
    void nullRevenueAmountReturnsDomainError() {
        BusinessException exception = assertThrows(BusinessException.class, () -> revenue(null, LocalDate.now()));

        assertEquals(AssetErrorCode.INVALID_REVENUE_AMOUNT, exception.getErrorCode());
    }

    @Test
    void nullRevenuePeriodReturnsDomainError() {
        BusinessException exception = assertThrows(BusinessException.class, () -> revenue(BigDecimal.TEN, null));

        assertEquals(AssetErrorCode.INVALID_REVENUE_PERIOD, exception.getErrorCode());
    }

    private Revenue revenue(BigDecimal grossAmount, LocalDate periodStart) {
        return new Revenue(
                UUID.randomUUID(), UUID.randomUUID(), RevenueSourceType.PROPERTY_MANAGER,
                "source-1", RevenueType.RENTAL_INCOME,
                grossAmount, BigDecimal.ZERO, BigDecimal.ZERO,
                "KRW", periodStart, LocalDate.now(), null);
    }
}
