package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 자산 등록·변경 서비스 */
@Service
@RequiredArgsConstructor
public class AssetCommandService {

    private final AssetRepository assetRepository;

    /** 자산 등록 */
    @Transactional
    public AssetCreateResponse createAsset(
            UUID userId,
            String role,
            AssetCreateRequest request
    ) {
        // 자산운용자와 관리자만 등록 가능
        if (!"ISSUER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_CREATE_ACCESS_DENIED
            );
        }

        // 요청한 사용자를 소유자로 설정
        Asset asset = new Asset(
                userId,
                request.assetName(),
                request.type(),
                request.description(),
                request.valuationAmount(),
                request.expectedReturnRate(),
                request.detailData(),
                request.totalShareQuantity()
        );

        asset.selectOwnerBurdenPaymentMethod(request.ownerBurdenPaymentMethod());

        // DRAFT 상태로 저장
        Asset savedAsset = assetRepository.save(asset);

        // 저장 결과 반환
        return AssetCreateResponse.from(savedAsset);
    }
}
