package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.request.HoldingRevocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResult;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResult;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingQueryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingCommandServiceTest {

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private HoldingHistoryRepository holdingHistoryRepository;

    @Mock
    private HoldingQueryRepository holdingQueryRepository;

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
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));
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
        verifyNoInteractions(assetQueryRepository);
        verify(holdingRepository, never()).save(any(Holding.class));
        verify(holdingHistoryRepository, never()).save(any(HoldingHistory.class));
    }

    @Test
    void duplicateCompletedWhileWaitingForAssetLockIsNotAllocatedAgain() {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        HoldingAllocationRequest request =
                new HoldingAllocationRequest(subscriptionId, assetId, userId, 10);

        Asset asset = approvedAsset(assetId);
        Holding holding = new Holding(assetId, userId, 10);
        ReflectionTestUtils.setField(holding, "id", holdingId);
        HoldingHistory history = new HoldingHistory(
                holdingId, subscriptionId, HoldingHistoryType.ALLOCATE,
                10, 0, 10, "ALLOCATE:" + subscriptionId, null
        );

        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.ALLOCATE
        )).thenReturn(Optional.empty(), Optional.of(history));
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(holding));

        HoldingAllocationResponse response = holdingCommandService.allocate(request);

        assertEquals(HoldingAllocationResult.ALREADY_PROCESSED, response.result());
        assertEquals(0, asset.getAllocatedQuantity());
        verify(holdingRepository, never()).save(any(Holding.class));
        verify(holdingHistoryRepository, never()).save(any(HoldingHistory.class));
    }

    @Test
    void deletedAssetCannotReceiveAllocation() {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        HoldingAllocationRequest request =
                new HoldingAllocationRequest(subscriptionId, assetId, UUID.randomUUID(), 10);

        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.ALLOCATE
        )).thenReturn(Optional.empty());
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> holdingCommandService.allocate(request)
        );

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(holdingRepository);
    }

    @Test
    @DisplayName("배정된 지분을 회수하고 회수 이력을 저장한다")
    void revokesSharesAndSavesHistory() {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        HoldingRevocationRequest request =
                new HoldingRevocationRequest(subscriptionId, "청약 취소");

        Asset asset = approvedAsset(assetId);
        asset.allocateShares(10);

        Holding holding = new Holding(assetId, userId, 15);
        ReflectionTestUtils.setField(holding, "id", holdingId);

        HoldingHistory allocationHistory = new HoldingHistory(
                holdingId, subscriptionId, HoldingHistoryType.ALLOCATE,
                10, 5, 15, "ALLOCATE:" + subscriptionId, null
        );

        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.REVOKE
        )).thenReturn(Optional.empty(), Optional.empty());
        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.ALLOCATE
        )).thenReturn(Optional.of(allocationHistory));
        when(holdingQueryRepository.findAssetIdByHoldingId(holdingId))
                .thenReturn(Optional.of(assetId));
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset));
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(holding));
        when(holdingRepository.save(holding)).thenReturn(holding);

        HoldingRevocationResponse response =
                holdingCommandService.revoke(holdingId, request);

        assertEquals(HoldingRevocationResult.REVOKED, response.result());
        assertEquals(10, response.quantity());
        assertEquals(5, holding.getQuantity());
        assertEquals(0, asset.getAllocatedQuantity());

        ArgumentCaptor<HoldingHistory> historyCaptor =
                ArgumentCaptor.forClass(HoldingHistory.class);
        verify(holdingHistoryRepository).save(historyCaptor.capture());

        HoldingHistory savedHistory = historyCaptor.getValue();
        assertEquals(HoldingHistoryType.REVOKE, savedHistory.getHistoryType());
        assertEquals(15, savedHistory.getBalanceBefore());
        assertEquals(5, savedHistory.getBalanceAfter());
        assertEquals("청약 취소", savedHistory.getReason());
    }

    @Test
    @DisplayName("이미 회수한 청약은 지분을 다시 차감하지 않는다")
    void duplicateRevocationDoesNotDecreaseQuantityAgain() {
        UUID subscriptionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        HoldingRevocationRequest request =
                new HoldingRevocationRequest(subscriptionId, "청약 취소");

        Holding holding = new Holding(assetId, userId, 5);
        ReflectionTestUtils.setField(holding, "id", holdingId);
        HoldingHistory revocationHistory = new HoldingHistory(
                holdingId, subscriptionId, HoldingHistoryType.REVOKE,
                10, 15, 5, "REVOKE:" + subscriptionId, "청약 취소"
        );

        when(holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                subscriptionId, HoldingHistoryType.REVOKE
        )).thenReturn(Optional.of(revocationHistory));
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(holding));

        HoldingRevocationResponse response =
                holdingCommandService.revoke(holdingId, request);

        assertEquals(HoldingRevocationResult.ALREADY_PROCESSED, response.result());
        assertEquals(5, holding.getQuantity());
        verifyNoInteractions(assetQueryRepository, holdingQueryRepository);
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
