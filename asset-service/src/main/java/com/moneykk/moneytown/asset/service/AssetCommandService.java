package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.request.AssetUpdateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 자산 등록·변경 서비스
 */
@Service
@RequiredArgsConstructor
public class AssetCommandService {

    private final AssetRepository assetRepository;
    private final AssetQueryRepository assetQueryRepository;

    /**
     * 자산 등록
     */
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

        // DRAFT 상태로 저장
        Asset savedAsset = assetRepository.save(asset);

        // 저장 결과 반환
        return AssetCreateResponse.from(savedAsset);
    }

    /**
     * 자산 정보 수정
     */
    @Transactional
    public void updateAsset(
            UUID assetId,
            UUID userId,
            String role,
            AssetUpdateRequest request
    ) {
        // 자산운용자와 관리자만 수정 가능
        if (userId == null
                || (!"ISSUER".equals(role) && !"ADMIN".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED
            );
        }

        // 잠금 조회로 동시 수정에 따른 변경 유실 방지
        Asset asset = assetQueryRepository.findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 관리자가 아니면 본인이 등록한 자산만 수정 가능
        if (!"ADMIN".equals(role) && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED
            );
        }

        // 평가금액은 수정 불가. null이나 기존과 같은 값도 요청에서 제외해야 한다.
        if (request.detail() != null && request.detail().containsKey("appraisalAmount")) {
            throw new BusinessException(AssetErrorCode.APPRAISAL_AMOUNT_UPDATE_NOT_ALLOWED);
        }

        // 상태 검사와 단가·차액 재계산은 엔티티에서 처리
        asset.updateInfo(
                request.name(),
                request.description(),
                request.ownerName(),
                request.detail(),
                null, // 기존 평가금액 유지. 단가·차액 재계산 로직은 그대로 사용
                request.totalShareQuantity()
        );

        // JPA 변경 감지로 저장
    }

}
