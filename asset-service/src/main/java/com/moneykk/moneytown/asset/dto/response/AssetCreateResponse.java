package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;

import java.time.Instant;
import java.util.UUID;

/** 자산 등록 결과 */
public record AssetCreateResponse(

        // 등록된 자산 ID
        UUID assetId,

        // 자산명
        String assetName,

        // 등록 직후 상태: DRAFT
        AssetStatus assetStatus,

        // 등록 시간
        Instant createdAt
) {

    /** 저장된 자산을 응답으로 변환 */
    public static AssetCreateResponse from(Asset asset) {
        return new AssetCreateResponse(
                asset.getId(),
                asset.getAssetName(),
                asset.getStatus(),
                asset.getCreatedAt()
        );
    }
}