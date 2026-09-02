package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 청약별 지분 배정·회수 이력 조회 서비스 */
@Service
@RequiredArgsConstructor
public class HoldingQueryService {

    private final HoldingHistoryRepository holdingHistoryRepository;
    private final HoldingRepository holdingRepository;

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

    // 이력 유형별 수량 합계
    private long quantityOf(List<HoldingHistory> histories, HoldingHistoryType type) {
        return histories.stream()
                .filter(history -> history.getHistoryType() == type)
                .mapToLong(HoldingHistory::getQuantity)
                .sum();
    }
}
