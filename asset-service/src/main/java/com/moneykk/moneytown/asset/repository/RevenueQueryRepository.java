package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Revenue;

import java.util.Optional;
import java.util.UUID;

public interface RevenueQueryRepository {

    // 자산 ID와 수익 ID로 수익 단건 조회
    Optional<Revenue> findByAssetIdAndRevenueId(
            UUID assetId,
            UUID revenueId
    );
}