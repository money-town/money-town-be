package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.RevenueCreateRequest;
import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueTransferStatusResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.RevenueRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 수익 상태 변경 서비스
 */
@Service
@RequiredArgsConstructor
public class RevenueCommandService {

    private final RevenueRepository revenueRepository;
    private final AssetQueryRepository assetQueryRepository;

    @Transactional
    public RevenueTransferStatusResponse updateTransferStatus(
            UUID revenueId,
            RevenueTransferStatusRequest request
    ) {
        // 변경할 수익 조회
        Revenue revenue = revenueRepository.findById(revenueId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.REVENUE_NOT_FOUND
                ));

        // 요청 상태에 맞게 엔티티 상태 변경
        switch (request.transferStatus()) {
            case TRANSFERRED -> revenue.markTransferred();
            case FAILED -> revenue.markFailed(request.failureReason());
            case READY -> revenue.retry();
        }

        // JPA 변경 감지로 UPDATE 처리
        return RevenueTransferStatusResponse.from(revenue);
    }

    /**
     * 자산 수익 등록
     */
    @Transactional
    public RevenueDetailResponse createRevenue(
            UUID assetId,
            UUID userId,
            String role,
            RevenueCreateRequest request
    ) {
        // 등록 가능한 역할인지 확인
        if (!"ISSUER".equals(role)
                && !"ADMIN".equals(role)
                && !"SYSTEM".equals(role)) {
            throw new BusinessException(AssetErrorCode.REVENUE_ACCESS_DENIED);
        }

        // 같은 자산의 동시 등록을 순차 처리
        Asset asset = assetQueryRepository.findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산운용자는 본인 자산에만 등록 가능
        if ("ISSUER".equals(role) && !asset.getUserId().equals(userId)) {
            throw new BusinessException(AssetErrorCode.REVENUE_ACCESS_DENIED);
        }

        // 동일 출처 수익의 중복 등록 방지
        boolean duplicated = revenueRepository
                .existsByAssetIdAndSourceTypeAndSourceReferenceId(
                        assetId,
                        request.sourceType(),
                        request.sourceReferenceId()
                );

        if (duplicated) {
            throw new BusinessException(AssetErrorCode.DUPLICATE_REVENUE);
        }

        // 금액·기간·통화 검증은 엔티티 생성자에서 처리
        Revenue revenue = new Revenue(
                assetId,
                userId,
                request.sourceType(),
                request.sourceReferenceId(),
                request.revenueType(),
                request.grossAmount(),
                request.expenseAmount(),
                request.feeAmount(),
                request.currency(),
                request.periodStart(),
                request.periodEnd(),
                request.rawPayload()
        );

        // READY 상태로 저장
        Revenue savedRevenue = revenueRepository.save(revenue);

        return RevenueDetailResponse.from(savedRevenue);
    }
}