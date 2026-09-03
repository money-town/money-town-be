package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;

import java.math.BigDecimal;
import java.util.UUID;

/** 자산 목록의 개별 항목 */
public record AssetListItemResponse(

        // 자산 ID
        UUID assetId,

        // 자산명
        String assetName,

        // 자산 유형
        AssetType assetType,

        // 평가 금액
        long valuationAmount,

        // 예상 수익률
        BigDecimal expectedReturnRate,

        // 지분 단가
        long unitPrice,

        // 전체 지분 수량
        long totalShareQuantity,

        // 자산 상태
        AssetStatus assetStatus
) {

    /** 자산을 목록 항목으로 변환 */
    public static AssetListItemResponse from(Asset asset) {
        return new AssetListItemResponse(
                asset.getId(),
                asset.getAssetName(),
                asset.getType(),
                asset.getValuationAmount(),
                asset.getExpectedReturnRate(),
                asset.getUnitPrice(),
                asset.getTotalShareQuantity(),
                asset.getStatus()
        );
    }
}