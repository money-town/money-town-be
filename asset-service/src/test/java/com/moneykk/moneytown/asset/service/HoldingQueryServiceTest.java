package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingQueryServiceTest {

    @Mock
    private HoldingHistoryRepository holdingHistoryRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @InjectMocks
    private HoldingQueryService holdingQueryService;

    @Test
    @DisplayName("처리 이력이 없으면 미처리 상태를 반환한다")
    void returnsNotProcessedWhenHistoryDoesNotExist() {
        UUID subscriptionId = UUID.randomUUID();
        when(holdingHistoryRepository.findAllBySubscriptionIdOrderByCreatedAtAsc(subscriptionId))
                .thenReturn(List.of());

        HoldingSubscriptionStatusResponse response =
                holdingQueryService.getSubscriptionStatus(subscriptionId);

        assertEquals(subscriptionId, response.subscriptionId());
        assertFalse(response.allocationProcessed());
        assertFalse(response.revocationProcessed());
        assertEquals(0, response.allocatedQuantity());
        assertEquals(0, response.revokedQuantity());
        assertNull(response.holdingId());
    }

    @Test
    @DisplayName("배정·회수 이력이 있으면 처리 결과를 반환한다")
    void returnsAllocationAndRevocationResultFromHistories() {
        UUID subscriptionId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant allocatedAt = Instant.parse("2026-09-01T01:00:00Z");
        Instant revokedAt = Instant.parse("2026-09-01T02:00:00Z");

        Holding holding = new Holding(assetId, userId, 0);
        ReflectionTestUtils.setField(holding, "id", holdingId);

        HoldingHistory allocation = new HoldingHistory(
                holdingId, subscriptionId, HoldingHistoryType.ALLOCATE,
                10, 0, 10, "allocate:" + subscriptionId, null
        );
        HoldingHistory revocation = new HoldingHistory(
                holdingId, subscriptionId, HoldingHistoryType.REVOKE,
                10, 10, 0, "revoke:" + subscriptionId, "OFFERING_ADMIN_CANCELLED"
        );
        ReflectionTestUtils.setField(allocation, "createdAt", allocatedAt);
        ReflectionTestUtils.setField(revocation, "createdAt", revokedAt);

        when(holdingHistoryRepository.findAllBySubscriptionIdOrderByCreatedAtAsc(subscriptionId))
                .thenReturn(List.of(allocation, revocation));
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(holding));

        HoldingSubscriptionStatusResponse response =
                holdingQueryService.getSubscriptionStatus(subscriptionId);

        assertEquals(holdingId, response.holdingId());
        assertEquals(assetId, response.assetId());
        assertEquals(userId, response.userId());
        assertEquals(10, response.allocatedQuantity());
        assertEquals(10, response.revokedQuantity());
        assertTrue(response.allocationProcessed());
        assertTrue(response.revocationProcessed());
        assertEquals(revokedAt, response.lastProcessedAt());
    }
}
