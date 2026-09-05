package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.dto.response.HoldingHistoryItemResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.dto.response.MyAssetHoldingResponse;
import com.moneykk.moneytown.asset.dto.response.MyHoldingItemResponse;
import com.moneykk.moneytown.asset.entity.Holding;
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

    // 특정 자산의 내 보유지분 조회
    Optional<MyAssetHoldingResponse> findMyHolding(
            UUID assetId,
            UUID userId
    );

    // 지분 변동 이력 조회
    List<HoldingHistoryItemResponse> findHoldingHistories(
            UUID holdingId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    );

    // 보유지분 소유자 ID 조회
    Optional<UUID> findUserIdByHoldingId(UUID holdingId);

    // 관리자 지분 조정용 잠금 조회
    Optional<Holding> findByIdForUpdate(UUID holdingId);

    // 내 전체 보유지분 목록 조회
    List<MyHoldingItemResponse> findMyHoldings(
            UUID userId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    );
}
