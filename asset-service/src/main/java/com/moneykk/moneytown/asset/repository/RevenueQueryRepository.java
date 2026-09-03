package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Revenue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RevenueQueryRepository {

    // 자산 ID와 수익 ID로 수익 단건 조회
    Optional<Revenue> findByAssetIdAndRevenueId(
            UUID assetId,
            UUID revenueId
    );

    // 정산 서비스에 전달할 READY 상태 수익 목록 조회
    List<Revenue> findReadyRevenues(
            UUID cursor,
            int limit
    );

    // 자산별 수익 목록 조회
    List<Revenue> findByAssetId(
            UUID assetId,
            UUID cursor,
            int limit
    );
}