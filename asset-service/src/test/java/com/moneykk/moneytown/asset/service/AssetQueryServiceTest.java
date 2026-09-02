package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.InternalAssetResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
