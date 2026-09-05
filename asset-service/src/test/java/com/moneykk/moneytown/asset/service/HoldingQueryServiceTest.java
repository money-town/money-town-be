package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.dto.response.MyAssetHoldingResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingQueryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingQueryServiceTest {

    @Mock
    private HoldingHistoryRepository holdingHistoryRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private HoldingQueryRepository holdingQueryRepository;

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @InjectMocks
    private HoldingQueryService holdingQueryService;

    @Test
    @DisplayName("투자자는 특정 자산의 내 보유지분을 조회한다")
    void returnsMyHolding() {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MyAssetHoldingResponse expected = new MyAssetHoldingResponse(
                UUID.randomUUID(), assetId, 25L, Instant.now()
        );
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(mock(Asset.class)));
        when(holdingQueryRepository.findMyHolding(assetId, userId))
                .thenReturn(Optional.of(expected));

        MyAssetHoldingResponse response = holdingQueryService
                .getMyHolding(assetId, userId, "INVESTOR");

        assertEquals(expected, response);
        verify(holdingQueryRepository).findMyHolding(assetId, userId);
    }

    @Test
    @DisplayName("보유지분이 없으면 수량 0을 반환한다")
    void returnsZeroWhenMyHoldingDoesNotExist() {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(mock(Asset.class)));
        when(holdingQueryRepository.findMyHolding(assetId, userId))
                .thenReturn(Optional.empty());

        MyAssetHoldingResponse response = holdingQueryService
                .getMyHolding(assetId, userId, "INVESTOR");

        assertNull(response.holdingId());
        assertEquals(assetId, response.assetId());
        assertEquals(0L, response.quantity());
        assertNull(response.updatedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "ISSUER", "SYSTEM"})
    @DisplayName("투자자가 아니면 내 보유지분을 조회할 수 없다")
    void rejectsMyHoldingForNonInvestor(String role) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> holdingQueryService.getMyHolding(
                        UUID.randomUUID(), UUID.randomUUID(), role
                )
        );

        assertEquals(
                AssetErrorCode.HOLDING_READ_ACCESS_DENIED,
                exception.getErrorCode()
        );
        verifyNoInteractions(assetQueryRepository, holdingQueryRepository);
    }

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

    @ParameterizedTest
    @CsvSource({
            "2026-08-31, 2026-08-31T15:00:00Z",
            "2026-12-31, 2026-12-31T15:00:00Z",
            "2028-02-29, 2028-02-29T15:00:00Z"
    })
    @DisplayName("월말·연말·윤일도 한국 시간 다음 날 0시를 조회 경계로 사용한다")
    void convertsRecordDateToExclusiveCutoff(LocalDate asOf, Instant expectedCutoff) {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(mock(Asset.class)));
        when(holdingQueryRepository.findSnapshotByAssetId(assetId, expectedCutoff, null, 101, Sort.Direction.DESC))
                .thenReturn(List.of());

        HoldingSnapshotResponse response = holdingQueryService.getSnapshot(assetId, asOf, null, 100, Sort.Direction.DESC);

        verify(holdingQueryRepository).findSnapshotByAssetId(assetId, expectedCutoff, null, 101, Sort.Direction.DESC);
        assertEquals(assetId, response.assetId());
        assertEquals(asOf, response.asOf());
        assertTrue(response.holdings().isEmpty());
        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("다음 페이지의 결과가 요청 크기 이하면 마지막 페이지로 반환한다")
    void returnsLastSnapshotPage(int count) {
        UUID assetId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        LocalDate asOf = LocalDate.of(2026, 8, 31);
        Instant cutoff = Instant.parse("2026-08-31T15:00:00Z");
        List<HoldingSnapshotItemResponse> rows = List.of(
                new HoldingSnapshotItemResponse(UUID.randomUUID(), UUID.randomUUID(), 10),
                new HoldingSnapshotItemResponse(UUID.randomUUID(), UUID.randomUUID(), 20)
        ).subList(0, count);
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(mock(Asset.class)));
        when(holdingQueryRepository.findSnapshotByAssetId(assetId, cutoff, cursor, 3, Sort.Direction.DESC))
                .thenReturn(rows);

        HoldingSnapshotResponse response = holdingQueryService.getSnapshot(assetId, asOf, cursor, 2, Sort.Direction.DESC);

        assertEquals(rows, response.holdings());
        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
        verify(holdingQueryRepository).findSnapshotByAssetId(assetId, cutoff, cursor, 3, Sort.Direction.DESC);
    }

    @Test
    @DisplayName("조회 가능한 자산이 없으면 스냅샷 쿼리를 호출하지 않는다")
    void rejectsSnapshotForMissingAsset() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> holdingQueryService.getSnapshot(assetId, LocalDate.of(2026, 8, 31), null, 100, Sort.Direction.DESC));

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(holdingQueryRepository);
    }

    @ParameterizedTest
    @EnumSource(Sort.Direction.class)
    @DisplayName("기준일 보유지분을 커서 방식으로 조회한다")
    void returnsHoldingSnapshotWithNextCursor(Sort.Direction direction) {
        UUID assetId = UUID.randomUUID();
        UUID firstHoldingId = UUID.randomUUID();
        UUID secondHoldingId = UUID.randomUUID();
        UUID thirdHoldingId = UUID.randomUUID();
        LocalDate asOf = LocalDate.of(2026, 9, 2);
        Instant cutoffExclusive = Instant.parse("2026-09-02T15:00:00Z");

        HoldingSnapshotItemResponse first = new HoldingSnapshotItemResponse(
                firstHoldingId, UUID.randomUUID(), 10
        );
        HoldingSnapshotItemResponse second = new HoldingSnapshotItemResponse(
                secondHoldingId, UUID.randomUUID(), 20
        );
        HoldingSnapshotItemResponse extra = new HoldingSnapshotItemResponse(
                thirdHoldingId, UUID.randomUUID(), 30
        );

        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Asset.class)));
        when(holdingQueryRepository.findSnapshotByAssetId(
                assetId, cutoffExclusive, null, 3, direction
        )).thenReturn(List.of(first, second, extra));

        HoldingSnapshotResponse response =
                holdingQueryService.getSnapshot(assetId, asOf, null, 2, direction);

        assertEquals(List.of(first, second), response.holdings());
        assertEquals(secondHoldingId, response.nextCursor());
        assertTrue(response.hasNext());
        assertEquals(asOf, response.asOf());
    }
}
