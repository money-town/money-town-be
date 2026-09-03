package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.InternalRevenueListResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueListResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.RevenueQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 수익 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class RevenueQueryService {

    private final RevenueQueryRepository revenueQueryRepository;
    private final AssetQueryRepository assetQueryRepository;

    @Transactional(readOnly = true)
    public RevenueDetailResponse getRevenue(
            UUID assetId,
            UUID revenueId
    ) {
        // 자산에 등록된 수익 조회
        Revenue revenue = revenueQueryRepository
                .findByAssetIdAndRevenueId(assetId, revenueId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.REVENUE_NOT_FOUND
                ));

        // 조회 결과를 응답 DTO로 변환
        return RevenueDetailResponse.from(revenue);
    }

    @Transactional(readOnly = true)
    public InternalRevenueListResponse getReadyRevenues(
            UUID cursor,
            int size,
            Sort.Direction direction
    ) {
        // 다음 페이지 존재 여부 확인을 위해 요청 크기보다 1개 더 조회
        List<Revenue> revenues =
                revenueQueryRepository.findReadyRevenues(cursor, size + 1, direction);

        boolean hasNext = revenues.size() > size;

        // 실제 응답에는 요청한 크기만큼만 포함
        List<RevenueDetailResponse> items = revenues.stream()
                .limit(size)
                .map(RevenueDetailResponse::from)
                .toList();

        // 다음 페이지가 있을 때 마지막 수익 ID를 커서로 반환
        UUID nextCursor = hasNext
                ? items.get(items.size() - 1).revenueId()
                : null;

        return new InternalRevenueListResponse(
                items,
                nextCursor,
                hasNext
        );
    }

    /**
     * 자산별 수익 목록 조회
     */
    @Transactional(readOnly = true)
    public RevenueListResponse getRevenues(
            UUID assetId,
            UUID userId,
            String role,
            UUID cursor,
            int size,
            Sort.Direction direction
    ) {
        // 자산운용자와 관리자만 조회 가능
        if (!"ISSUER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(AssetErrorCode.REVENUE_ACCESS_DENIED);
        }

        // 삭제되지 않은 자산 조회
        Asset asset = assetQueryRepository.findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산운용자는 본인 자산만 조회 가능
        if ("ISSUER".equals(role) && !asset.getUserId().equals(userId)) {
            throw new BusinessException(AssetErrorCode.REVENUE_ACCESS_DENIED);
        }

        // 다음 페이지 확인을 위해 1건 더 조회
        List<Revenue> revenues = revenueQueryRepository
                .findByAssetId(assetId, cursor, size + 1, direction);

        boolean hasNext = revenues.size() > size;

        // 요청한 개수만 응답에 포함
        List<RevenueDetailResponse> items = revenues.stream()
                .limit(size)
                .map(RevenueDetailResponse::from)
                .toList();

        // 다음 페이지가 있으면 마지막 수익 ID 반환
        UUID nextCursor = hasNext
                ? items.get(items.size() - 1).revenueId()
                : null;

        return new RevenueListResponse(items, nextCursor, hasNext);
    }
}
