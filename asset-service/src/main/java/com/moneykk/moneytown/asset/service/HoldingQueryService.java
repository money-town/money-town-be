package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.dto.response.MyAssetHoldingResponse;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingQueryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 청약별 지분 배정·회수 이력 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class HoldingQueryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final HoldingHistoryRepository holdingHistoryRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingQueryRepository holdingQueryRepository;
    private final AssetQueryRepository assetQueryRepository;

    @Transactional(readOnly = true)
    public HoldingSubscriptionStatusResponse getSubscriptionStatus(UUID subscriptionId) {
        // 청약 ID로 지분 이력 조회
        List<HoldingHistory> histories =
                holdingHistoryRepository.findAllBySubscriptionIdOrderByCreatedAtAsc(subscriptionId);

        // 처리 이력이 없으면 미처리 상태 반환
        if (histories.isEmpty()) {
            return new HoldingSubscriptionStatusResponse(
                    subscriptionId, null, null, null,
                    0, 0, false, false, null
            );
        }

        UUID holdingId = histories.get(0).getHoldingId();

        // 같은 청약의 이력이 서로 다른 보유지분을 가리키는지 확인
        boolean hasDifferentHolding = histories.stream()
                .anyMatch(history -> !holdingId.equals(history.getHoldingId()));
        if (hasDifferentHolding) {
            throw new BusinessException(AssetErrorCode.HOLDING_DATA_CONFLICT);
        }

        // 현재 보유지분 조회
        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new BusinessException(AssetErrorCode.HOLDING_DATA_CONFLICT));

        // 배정·회수 수량 계산
        long allocatedQuantity = quantityOf(histories, HoldingHistoryType.ALLOCATE);
        long revokedQuantity = quantityOf(histories, HoldingHistoryType.REVOKE);
        Instant lastProcessedAt = histories.get(histories.size() - 1).getCreatedAt();

        return new HoldingSubscriptionStatusResponse(
                subscriptionId,
                holdingId,
                holding.getAssetId(),
                holding.getUserId(),
                allocatedQuantity,
                revokedQuantity,
                allocatedQuantity > 0,
                revokedQuantity > 0,
                lastProcessedAt
        );
    }

    @Transactional(readOnly = true)
    public HoldingSnapshotResponse getSnapshot(
            UUID assetId,
            LocalDate asOf,
            UUID cursor,
            int size,
            Sort.Direction direction
    ) {
        // 삭제되지 않은 자산인지 확인
        assetQueryRepository.findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));

        // 기준일 당일 이력까지 포함하기 위한 다음 날 00시
        Instant cutoffExclusive = asOf.plusDays(1)
                .atStartOfDay(SERVICE_ZONE)
                .toInstant();

        // 다음 페이지 존재 여부 확인을 위해 한 건 더 조회
        List<HoldingSnapshotItemResponse> rows =
                holdingQueryRepository.findSnapshotByAssetId(
                        assetId,
                        cutoffExclusive,
                        cursor,
                        size + 1,
                        direction
                );

        boolean hasNext = rows.size() > size;
        List<HoldingSnapshotItemResponse> holdings = hasNext
                ? List.copyOf(rows.subList(0, size))
                : List.copyOf(rows);
        UUID nextCursor = hasNext
                ? holdings.get(holdings.size() - 1).holdingId()
                : null;

        return new HoldingSnapshotResponse(
                assetId,
                asOf,
                holdings,
                nextCursor,
                hasNext
        );
    }

    // 이력 유형별 수량 합계
    private long quantityOf(List<HoldingHistory> histories, HoldingHistoryType type) {
        return histories.stream()
                .filter(history -> history.getHistoryType() == type)
                .mapToLong(HoldingHistory::getQuantity)
                .sum();
    }

    /**
     * 특정 자산의 내 보유지분 조회
     */
    @Transactional(readOnly = true)
    public MyAssetHoldingResponse getMyHolding(
            UUID assetId,
            UUID userId,
            String role
    ) {
        // 투자자만 자신의 보유지분 조회 가능
        if (userId == null || !"INVESTOR".equals(role)) {
            throw new BusinessException(
                    AssetErrorCode.HOLDING_READ_ACCESS_DENIED
            );
        }

        // 삭제되지 않은 자산인지 확인
        assetQueryRepository.findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 보유지분이 없으면 수량 0으로 응답
        return holdingQueryRepository
                .findMyHolding(assetId, userId)
                .orElseGet(() -> new MyAssetHoldingResponse(
                        null,
                        assetId,
                        0L,
                        null
                ));
    }
}
