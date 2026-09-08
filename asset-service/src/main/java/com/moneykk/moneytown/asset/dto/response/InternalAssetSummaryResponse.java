package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** AI 추천에 사용하는 내부 자산 요약 응답 */
public record InternalAssetSummaryResponse(

        UUID assetId,

        AssetType assetType,

        String assetName,

        AssetStatus assetStatus,

        BigDecimal expectedReturnRate,

        long valuationAmount,

        String description,

        Map<String, Object> detailData
) {

    /** 자산 엔티티를 내부 요약 응답으로 변환 */
    public static InternalAssetSummaryResponse from(Asset asset) {
        return new InternalAssetSummaryResponse(
                asset.getId(),
                asset.getType(),
                asset.getAssetName(),
                asset.getStatus(),
                asset.getExpectedReturnRate(),
                asset.getValuationAmount(),
                asset.getDescription(),
                new HashMap<>(asset.getDetailData())
        );
    }
}