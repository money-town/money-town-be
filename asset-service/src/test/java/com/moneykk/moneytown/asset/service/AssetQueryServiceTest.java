package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.InternalAssetResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import com.moneykk.moneytown.asset.dto.response.AssetListItemResponse;
import com.moneykk.moneytown.asset.dto.response.AssetListResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetQueryServiceTest {

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @InjectMocks
    private AssetQueryService assetQueryService;

    @Test
    @DisplayName("삭제되지 않은 자산 정보를 조회한다")
    void getsInternalAsset() {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Asset asset = new Asset(
                userId,
                "테스트 부동산",
                AssetType.REAL_ESTATE,
                "테스트 자산",
                100_000_000L,
                BigDecimal.valueOf(5),
                Map.of(),
                10_000L,
                10_000L
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        ReflectionTestUtils.setField(asset, "status", AssetStatus.APPROVED);

        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset));

        InternalAssetResponse response =
                assetQueryService.getInternalAsset(assetId);

        assertEquals(assetId, response.assetId());
        assertEquals(userId, response.userId());
        assertEquals(AssetType.REAL_ESTATE, response.assetType());
        assertEquals("테스트 부동산", response.assetName());
        assertEquals(10_000L, response.unitPrice());
        assertEquals(10_000L, response.totalShareQuantity());
        assertEquals(0, response.allocatedQuantity());
        assertEquals(AssetStatus.APPROVED, response.assetStatus());
    }

    @ParameterizedTest
    @CsvSource({"ADMIN, ASC", "ADMIN, DESC", "ISSUER, ASC", "ISSUER, DESC",
            "INVESTOR, ASC", "INVESTOR, DESC"})
    @DisplayName("역할별 조회 범위와 정렬 방향을 전달하고 다음 커서를 반환한다")
    void getsAssetsWithRoleScope(String role, Sort.Direction direction) {
        UUID userId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        UUID ownerId = "ISSUER".equals(role) ? userId : null;
        AssetStatus status = "INVESTOR".equals(role) ? AssetStatus.APPROVED : null;
        Asset first = asset(userId);
        Asset second = asset(userId);
        Asset extra = asset(userId);
        when(assetQueryRepository.findAssets(ownerId, status, cursor, 3, direction))
                .thenReturn(List.of(first, second, extra));

        AssetListResponse response = assetQueryService.getAssets(userId, role, cursor, 2, direction);

        assertEquals(List.of(AssetListItemResponse.from(first), AssetListItemResponse.from(second)),
                response.assets());
        assertTrue(response.hasNext());
        assertEquals(second.getId(), response.nextCursor());
        verify(assetQueryRepository).findAssets(ownerId, status, cursor, 3, direction);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("결과가 요청 크기 이하면 마지막 페이지로 반환한다")
    void returnsLastOrEmptyPage(int count) {
        UUID userId = UUID.randomUUID();
        List<Asset> rows = List.of(asset(userId), asset(userId)).subList(0, count);
        when(assetQueryRepository.findAssets(null, null, null, 3, Sort.Direction.DESC))
                .thenReturn(rows);

        AssetListResponse response = assetQueryService.getAssets(userId, "ADMIN", null, 2, Sort.Direction.DESC);

        assertEquals(count, response.assets().size());
        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"SYSTEM", "UNKNOWN", "admin"})
    @DisplayName("허용되지 않은 역할이면 목록을 조회하지 않는다")
    void rejectsUnauthorizedRole(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAssets(UUID.randomUUID(), role, null, 20, Sort.Direction.DESC));

        assertEquals(AssetErrorCode.ASSET_READ_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetQueryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ISSUER", "INVESTOR"})
    @DisplayName("사용자 ID가 없으면 조회하지 않는다")
    void rejectsMissingUser(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAssets(null, role, null, 20, Sort.Direction.DESC));

        assertEquals(AssetErrorCode.ASSET_READ_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetQueryRepository);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN, DRAFT", "ADMIN, APPROVED", "ISSUER, DRAFT", "ISSUER, APPROVED",
            "INVESTOR, APPROVED"})
    @DisplayName("권한이 있는 사용자는 자산 상세 정보를 조회한다")
    void getsAssetDetail(String role, AssetStatus status) {
        UUID userId = UUID.randomUUID();
        Asset asset = asset("ISSUER".equals(role) ? userId : UUID.randomUUID());
        Instant createdAt = Instant.parse("2026-09-01T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-02T02:00:00Z");
        ReflectionTestUtils.setField(asset, "status", status);
        ReflectionTestUtils.setField(asset, "createdAt", createdAt);
        ReflectionTestUtils.setField(asset, "updatedAt", updatedAt);
        asset.getDetailData().put("address", "서울");
        when(assetQueryRepository.findActiveById(asset.getId())).thenReturn(Optional.of(asset));

        AssetDetailResponse response = assetQueryService.getAsset(asset.getId(), userId, role);

        assertEquals(asset.getId(), response.assetId());
        assertEquals(asset.getUserId(), response.userId());
        assertEquals(asset.getAssetName(), response.assetName());
        assertEquals(asset.getType(), response.assetType());
        assertEquals(asset.getDescription(), response.description());
        assertEquals(asset.getValuationAmount(), response.valuationAmount());
        assertEquals(asset.getExpectedReturnRate(), response.expectedReturnRate());
        assertEquals(Map.of("address", "서울"), response.detailData());
        assertEquals(asset.getUnitPrice(), response.unitPrice());
        assertEquals(asset.getTotalShareQuantity(), response.totalShareQuantity());
        assertEquals(asset.getAllocatedQuantity(), response.allocatedQuantity());
        assertEquals(status, response.assetStatus());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
        // 응답의 최상위 맵 변경이 엔티티에 반영되지 않는지 확인
        response.detailData().put("address", "부산");
        assertEquals("서울", asset.getDetailData().get("address"));
    }

    @Test
    @DisplayName("자산운용자는 다른 소유자의 자산 상세를 조회할 수 없다")
    void rejectsOtherOwnersAssetDetail() {
        Asset asset = asset(UUID.randomUUID());
        when(assetQueryRepository.findActiveById(asset.getId())).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAsset(asset.getId(), UUID.randomUUID(), "ISSUER"));

        assertEquals(AssetErrorCode.ASSET_READ_ACCESS_DENIED, exception.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = AssetStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("투자자는 승인 상태가 아닌 자산 상세를 조회할 수 없다")
    void rejectsUnapprovedAssetDetail(AssetStatus status) {
        Asset asset = asset(UUID.randomUUID());
        ReflectionTestUtils.setField(asset, "status", status);
        when(assetQueryRepository.findActiveById(asset.getId())).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAsset(asset.getId(), UUID.randomUUID(), "INVESTOR"));

        assertEquals(AssetErrorCode.ASSET_READ_ACCESS_DENIED, exception.getErrorCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"SYSTEM", "UNKNOWN", "admin"})
    @DisplayName("상세 조회 권한이 없으면 저장소를 호출하지 않는다")
    void rejectsUnauthorizedDetailRole(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAsset(UUID.randomUUID(), UUID.randomUUID(), role));

        assertEquals(AssetErrorCode.ASSET_READ_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetQueryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ISSUER", "INVESTOR"})
    @DisplayName("사용자 ID가 없으면 상세 조회를 차단한다")
    void rejectsDetailWithoutUser(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAsset(UUID.randomUUID(), null, role));

        assertEquals(AssetErrorCode.ASSET_READ_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetQueryRepository);
    }

    @Test
    @DisplayName("조회 가능한 자산이 없으면 상세 조회에서 ASSET_NOT_FOUND를 반환한다")
    void rejectsMissingAssetDetail() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetQueryService.getAsset(assetId, UUID.randomUUID(), "ADMIN"));

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
    }

    private Asset asset(UUID ownerId) {
        Asset asset = new Asset(ownerId, "테스트 자산", AssetType.REAL_ESTATE, "자산 설명",
                100_000_000L, BigDecimal.valueOf(5), Map.of(), 10_000L, 10_000L);
        ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(asset, "status", AssetStatus.APPROVED);
        return asset;
    }

    @Test
    @DisplayName("자산이 없으면 ASSET_NOT_FOUND 예외를 반환한다")
    void throwsWhenAssetDoesNotExist() {
        UUID assetId = UUID.randomUUID();

        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetQueryService.getInternalAsset(assetId)
        );

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
    }
}
