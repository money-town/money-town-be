package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 자산 상세 조회 응답 */
public record AssetDetailResponse(

        // 자산 ID
        UUID assetId,

        // 자산운용자 ID
        UUID userId,

        // 자산명
        String assetName,

        // 자산 유형
        AssetType assetType,

        // 자산 설명
        String description,

        // 평가 금액
        long valuationAmount,

        // 예상 수익률
        BigDecimal expectedReturnRate,

        // 자산 유형별 상세 정보
        Map<String, Object> detailData,

        // 지분 단가
        long unitPrice,

        // 전체 지분 수량
        long totalShareQuantity,

        // 배정된 지분 수량
        long allocatedQuantity,

        // 자산 상태
        AssetStatus assetStatus,

        // 등록 시간
        Instant createdAt,

        // 최종 수정 시간
        Instant updatedAt
) {

    /** 자산 엔티티를 상세 응답으로 변환 */
    public static AssetDetailResponse from(Asset asset) {
        return new AssetDetailResponse(
                asset.getId(),
                asset.getUserId(),
                asset.getAssetName(),
                asset.getType(),
                asset.getDescription(),
                asset.getValuationAmount(),
                asset.getExpectedReturnRate(),
                new HashMap<>(asset.getDetailData()),
                asset.getUnitPrice(),
                asset.getTotalShareQuantity(),
                asset.getAllocatedQuantity(),
                asset.getStatus(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}