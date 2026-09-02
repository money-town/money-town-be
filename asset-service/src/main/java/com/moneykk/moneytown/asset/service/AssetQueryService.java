package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.InternalAssetResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 자산 조회 서비스 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final AssetQueryRepository assetQueryRepository;

    @Transactional(readOnly = true)
    public InternalAssetResponse getInternalAsset(UUID assetId) {
        // 삭제되지 않은 자산 조회
        Asset asset = assetQueryRepository.findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 내부 API 응답 생성
        return new InternalAssetResponse(
                asset.getId(),
                asset.getUserId(),
                asset.getType(),
                asset.getAssetName(),
                asset.getUnitPrice(),
                asset.getTotalShareQuantity(),
                asset.getAllocatedQuantity(),
                asset.getStatus()
        );
    }
}