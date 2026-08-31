package com.moneykk.moneytown.asset.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetEntityTest {

    @Test
    void holdingQuantityCannotBecomeNegative() {
        Holding holding = new Holding(UUID.randomUUID(), UUID.randomUUID(), 10);

        holding.allocate(5);
        holding.revoke(3);

        assertEquals(12, holding.getQuantity());
        assertThrows(IllegalArgumentException.class, () -> holding.revoke(13));
    }

    @Test
    void allocationHistoryRequiresSubscriptionId() {
        assertThrows(IllegalArgumentException.class, () -> new HoldingHistory(
                UUID.randomUUID(), null, HoldingHistoryType.ALLOCATE,
                10, 0, 10, "allocate-1", null));
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
}
