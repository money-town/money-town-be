package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.OfferingCompletionRequest;
import com.moneykk.moneytown.asset.dto.response.OwnerBurdenQuoteResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerBurdenService {
    private final AssetQueryRepository assetQueryRepository;

    @Transactional
    public void recordOfferingCompletion(UUID assetId, String role, OfferingCompletionRequest request) {
        requireSystem(role);
        if (request.completedAt() == null || request.completedAt().isAfter(Instant.now())) {
            throw new BusinessException(AssetErrorCode.INVALID_OFFERING_COMPLETION);
        }
        // 동시 통지와 재시도로 이자 시작 시점이 바뀌지 않도록 행 잠금
        Asset asset = assetQueryRepository.findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
        asset.recordOfferingCompletion(request.offeringId(), request.completedAt());
    }

    @Transactional(readOnly = true)
    public OwnerBurdenQuoteResponse getQuote(UUID assetId, String role, LocalDate asOf) {
        requireSystem(role);
        Asset asset = assetQueryRepository.findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
        if (asset.getOfferingCompletedAt() == null) {
            throw new BusinessException(AssetErrorCode.OFFERING_NOT_COMPLETED);
        }
        LocalDate startDate = asset.getOfferingCompletedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        if (asOf == null || asOf.isBefore(startDate)) {
            throw new BusinessException(AssetErrorCode.INVALID_OWNER_BURDEN_DATE);
        }
        BigDecimal interest = asset.calculateOwnerBurdenInterest(asOf);
        return new OwnerBurdenQuoteResponse(asset.getId(), asset.getUserId(), asset.getCompletedOfferingId(),
                asset.getOwnerBurdenPaymentMethod(), asset.getOfferingCompletedAt(), asOf,
                asset.getOwnerBurdenPrincipal(), interest,
                BigDecimal.valueOf(asset.getOwnerBurdenPrincipal()).add(interest));
    }

    private void requireSystem(String role) {
        if (!"SYSTEM".equals(role)) {
            throw new BusinessException(AssetErrorCode.OWNER_BURDEN_ACCESS_DENIED);
        }
    }
}
