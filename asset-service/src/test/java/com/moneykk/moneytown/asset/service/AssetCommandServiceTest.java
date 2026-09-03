package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetCommandServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private AssetCommandService assetCommandService;

    @ParameterizedTest
    @CsvSource({
            "ISSUER, REAL_ESTATE",
            "ISSUER, MUSIC_COPYRIGHT",
            "ADMIN, REAL_ESTATE",
            "ADMIN, MUSIC_COPYRIGHT"
    })
    @DisplayName("운용자와 관리자는 본인 소유 자산을 DRAFT 상태로 등록한다")
    void createsAsset(String role, AssetType type) {
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-03T01:00:00Z");
        AssetCreateRequest request = request(type);
        // DB에서 생성되는 값은 테스트에서 지정
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", assetId);
            ReflectionTestUtils.setField(asset, "createdAt", createdAt);
            return asset;
        });

        AssetCreateResponse response = assetCommandService.createAsset(userId, role, request);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(request.assetName(), saved.getAssetName());
        assertEquals(type, saved.getType());
        assertEquals(request.description(), saved.getDescription());
        assertEquals(request.valuationAmount().longValue(), saved.getValuationAmount());
        assertEquals(request.expectedReturnRate(), saved.getExpectedReturnRate());
        assertEquals(request.detailData(), saved.getDetailData());
        assertEquals(request.unitPrice().longValue(), saved.getUnitPrice());
        assertEquals(request.totalShareQuantity().longValue(), saved.getTotalShareQuantity());
        assertEquals(0L, saved.getAllocatedQuantity());
        assertEquals(AssetStatus.DRAFT, saved.getStatus());
        assertEquals(new AssetCreateResponse(assetId, request.assetName(), AssetStatus.DRAFT, createdAt),
                response);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"INVESTOR", "SYSTEM", "issuer", " ADMIN "})
    @DisplayName("등록 권한이 없으면 자산을 저장하지 않는다")
    void rejectsUnauthorizedRole(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.createAsset(UUID.randomUUID(), role, request(AssetType.REAL_ESTATE)));

        assertEquals(AssetErrorCode.ASSET_CREATE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetRepository);
    }

    private AssetCreateRequest request(AssetType type) {
        return new AssetCreateRequest(
                "테스트 자산", type, "자산 설명", 100_000_000L,
                new BigDecimal("5.2500"), Map.of("description", "상세 정보"),
                10_000L, 10_000L
        );
    }
}
