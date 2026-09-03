package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import com.moneykk.moneytown.asset.dto.response.AssetListItemResponse;
import com.moneykk.moneytown.asset.dto.response.AssetListResponse;
import com.moneykk.moneytown.asset.dto.response.InternalAssetResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 자산 조회 서비스
 */
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
        return InternalAssetResponse.of(asset);
    }

    /**
     * 자산 목록 조회
     */
    @Transactional(readOnly = true)
    public AssetListResponse getAssets(
            UUID userId,
            String role,
            UUID cursor,
            int size,
            Sort.Direction direction
    ) {
        // 사용자 정보와 조회 권한 확인
        if (userId == null
                || (!"ADMIN".equals(role)
                && !"ISSUER".equals(role)
                && !"INVESTOR".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_READ_ACCESS_DENIED
            );
        }

        // 자산운용자는 본인 소유 자산만 조회
        UUID ownerId = "ISSUER".equals(role) ? userId : null;

        // 투자자는 승인된 자산만 조회
        AssetStatus status = "INVESTOR".equals(role)
                ? AssetStatus.APPROVED
                : null;

        // 다음 페이지 확인을 위해 1건 더 조회
        List<Asset> assets = assetQueryRepository.findAssets(
                ownerId,
                status,
                cursor,
                size + 1,
                direction
        );

        boolean hasNext = assets.size() > size;

        // 요청한 개수만 응답에 포함
        List<AssetListItemResponse> items = assets.stream()
                .limit(size)
                .map(AssetListItemResponse::from)
                .toList();

        // 마지막 자산 ID를 다음 커서로 반환
        UUID nextCursor = hasNext
                ? items.get(items.size() - 1).assetId()
                : null;

        return new AssetListResponse(items, nextCursor, hasNext);
    }

    /**
     * 자산 상세 조회
     */
    @Transactional(readOnly = true)
    public AssetDetailResponse getAsset(
            UUID assetId,
            UUID userId,
            String role
    ) {
        // 사용자 정보와 조회 권한 확인
        if (userId == null
                || (!"ADMIN".equals(role)
                && !"ISSUER".equals(role)
                && !"INVESTOR".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_READ_ACCESS_DENIED
            );
        }

        // 삭제되지 않은 자산 조회
        Asset asset = assetQueryRepository.findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산운용자는 본인 소유 자산만 조회
        if ("ISSUER".equals(role) && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_READ_ACCESS_DENIED
            );
        }

        // 투자자는 승인된 자산만 조회
        if ("INVESTOR".equals(role)
                && asset.getStatus() != AssetStatus.APPROVED) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_READ_ACCESS_DENIED
            );
        }

        // 상세 응답으로 변환
        return AssetDetailResponse.from(asset);
    }
}
