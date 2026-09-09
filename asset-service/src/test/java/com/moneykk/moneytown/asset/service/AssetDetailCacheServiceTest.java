package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.global.config.AssetRedisCacheConfig;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetDetailCacheServiceTest {

    private AnnotationConfigApplicationContext context;
    private AssetQueryRepository assetQueryRepository;
    private AssetDetailCacheService assetDetailCacheService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        assetQueryRepository = context.getBean(AssetQueryRepository.class);
        assetDetailCacheService = context.getBean(AssetDetailCacheService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("같은 자산 상세를 두 번 조회하면 저장소는 한 번만 호출한다")
    void cachesAssetDetail() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId);
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset));

        AssetDetailResponse first = assetDetailCacheService.getAsset(assetId);
        AssetDetailResponse second = assetDetailCacheService.getAsset(assetId);

        assertEquals(first, second);
        verify(assetQueryRepository, times(1)).findActiveById(assetId);
    }

    @Test
    @DisplayName("존재하지 않는 자산 조회 실패는 캐시하지 않는다")
    void doesNotCacheMissingAsset() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.empty());

        BusinessException first = assertThrows(BusinessException.class,
                () -> assetDetailCacheService.getAsset(assetId));
        BusinessException second = assertThrows(BusinessException.class,
                () -> assetDetailCacheService.getAsset(assetId));

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, first.getErrorCode());
        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, second.getErrorCode());
        verify(assetQueryRepository, times(2)).findActiveById(assetId);
    }

    private Asset asset(UUID assetId) {
        Asset asset = new Asset(
                UUID.randomUUID(),
                "테스트 자산",
                AssetType.REAL_ESTATE,
                "테스트 설명",
                100_000L,
                new BigDecimal("5.0000"),
                Map.of("address", "서울"),
                100L
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        AssetQueryRepository assetQueryRepository() {
            return mock(AssetQueryRepository.class);
        }

        @Bean
        AssetDetailCacheService assetDetailCacheService(
                AssetQueryRepository assetQueryRepository
        ) {
            return new AssetDetailCacheService(assetQueryRepository);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    AssetRedisCacheConfig.ASSET_DETAIL_CACHE
            );
        }
    }
}
