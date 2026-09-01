package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResult;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingCommandServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private HoldingHistoryRepository holdingHistoryRepository;

    @InjectMocks
    private HoldingCommandService holdingCommandService;

    @Test
    void allocatesSharesAndSavesHistory() {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        HoldingAllocationRequest request =
                new HoldingAllocationRequest(subscriptionId, assetId, userId, 10);

        Asset asset = approvedAsset(assetId);
        Holding holding = new Holding(assetId, userId, 5);
        ReflectionTestUtils.setField(holding, "id", holdingId);

        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.ALLOCATE
        )).thenReturn(Optional.empty());
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(holdingRepository.findByAssetIdAndUserId(assetId, userId))
                .thenReturn(Optional.of(holding));
        when(holdingRepository.save(holding)).thenReturn(holding);

        HoldingAllocationResponse response = holdingCommandService.allocate(request);

        assertEquals(HoldingAllocationResult.ALLOCATED, response.result());
        assertEquals(10, response.quantity());
        assertEquals(15, holding.getQuantity());
        assertEquals(10, asset.getAllocatedQuantity());

        ArgumentCaptor<HoldingHistory> historyCaptor =
                ArgumentCaptor.forClass(HoldingHistory.class);
        verify(holdingHistoryRepository).save(historyCaptor.capture());

        HoldingHistory savedHistory = historyCaptor.getValue();
        assertEquals(HoldingHistoryType.ALLOCATE, savedHistory.getHistoryType());
        assertEquals(5, savedHistory.getBalanceBefore());
        assertEquals(15, savedHistory.getBalanceAfter());
    }

    @Test
    void duplicateAllocationDoesNotIncreaseQuantityAgain() {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        HoldingAllocationRequest request =
                new HoldingAllocationRequest(subscriptionId, assetId, userId, 10);

        Holding holding = new Holding(assetId, userId, 10);
        ReflectionTestUtils.setField(holding, "id", holdingId);
        HoldingHistory history = new HoldingHistory(
                holdingId, subscriptionId, HoldingHistoryType.ALLOCATE,
                10, 0, 10, "ALLOCATE:" + subscriptionId, null
        );

        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.ALLOCATE
        )).thenReturn(Optional.of(history));
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(holding));

        HoldingAllocationResponse response = holdingCommandService.allocate(request);

        assertEquals(HoldingAllocationResult.ALREADY_PROCESSED, response.result());
        assertEquals(10, holding.getQuantity());
        verifyNoInteractions(assetRepository);
        verify(holdingRepository, never()).save(any(Holding.class));
        verify(holdingHistoryRepository, never()).save(any(HoldingHistory.class));
    }

    private Asset approvedAsset(UUID assetId) {
        Asset asset = new Asset(
                UUID.randomUUID(), "테스트 부동산", AssetType.REAL_ESTATE, "테스트 자산",
                100_000_000L, BigDecimal.valueOf(5), Map.of(), 10_000L, 100L
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        ReflectionTestUtils.setField(asset, "status", AssetStatus.APPROVED);
        return asset;
    }
}
