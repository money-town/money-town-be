package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetQueryRepository {

    // 삭제되지 않은 자산 조회
    Optional<Asset> findActiveById(UUID assetId);

    // 지분 배정·회수용 잠금 조회
    Optional<Asset> findActiveByIdForUpdate(UUID assetId);

    // 삭제되지 않은 자산 목록 조회
    List<Asset> findAssets(
            UUID ownerId,
            AssetStatus status,
            UUID cursor,
            int limit,
            Sort.Direction direction
    );
}