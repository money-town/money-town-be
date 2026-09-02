package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;

import java.util.UUID;

/** 공모 등록 전 자산 검증에 사용하는 내부 응답 */
public record InternalAssetResponse(

        // 자산 ID
        UUID assetId,

        // 자산운용자 ID
        UUID userId,

        // 자산 유형
        AssetType assetType,

        // 자산명
        String assetName,

        // 한 조각당 가격
        long unitPrice,

        // 전체 발행 지분 수량
        long totalShareQuantity,

        // 현재 배정된 지분 수량
        long allocatedQuantity,

        // 자산 상태
        AssetStatus assetStatus
) {

    /** 자산 엔티티를 내부 조회 응답으로 변환 */
    public static InternalAssetResponse of(Asset asset) {
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
