package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldingQueryRepository {

    // 보유지분으로 자산 ID 조회
    Optional<UUID> findAssetIdByHoldingId(UUID holdingId);

    // 기준 시점까지의 투자자별 보유지분 조회
    List<HoldingSnapshotItemResponse> findSnapshotByAssetId(
            UUID assetId,
            Instant cutoffExclusive,
            UUID cursor,
            int limit,
            Sort.Direction direction
    );
}
