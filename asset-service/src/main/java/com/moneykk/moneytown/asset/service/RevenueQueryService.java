package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.RevenueQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 수익 조회 서비스 */
@Service
@RequiredArgsConstructor
public class RevenueQueryService {

    private final RevenueQueryRepository revenueQueryRepository;

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
}