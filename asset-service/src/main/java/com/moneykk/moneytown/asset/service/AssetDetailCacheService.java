package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.global.config.AssetRedisCacheConfig;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 자산 상세 정보를 Redis에서 조회한다. */
@Service
@RequiredArgsConstructor
public class AssetDetailCacheService {

    private final AssetQueryRepository assetQueryRepository;

    @Cacheable(
            cacheNames = AssetRedisCacheConfig.ASSET_DETAIL_CACHE,
            key = "#assetId"
    )
    @Transactional(readOnly = true)
    public AssetDetailResponse getAsset(UUID assetId) {
        // 캐시에 없을 때만 DB에서 조회
        Asset asset = assetQueryRepository
                .findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        return AssetDetailResponse.from(asset);
    }
}