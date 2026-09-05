package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.AssetType;

import java.time.Instant;
import java.util.UUID;

/** 내 보유지분 목록 항목 */
public record MyHoldingItemResponse(

        UUID holdingId,

        UUID assetId,

        String assetName,

        AssetType assetType,

        long quantity,

        Instant updatedAt
) {
}