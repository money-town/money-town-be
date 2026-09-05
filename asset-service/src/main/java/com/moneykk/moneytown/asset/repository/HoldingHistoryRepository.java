package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 지분 변동 이력 Repository
 */
public interface HoldingHistoryRepository extends JpaRepository<HoldingHistory, UUID> {

    // 청약 ID로 이력을 조회하고 생성 시간순 정렬
    List<HoldingHistory> findAllBySubscriptionIdOrderByCreatedAtAsc(UUID subscriptionId);

    // 같은 청약의 ALLOCATE 중복 처리를 방지함
    Optional<HoldingHistory> findBySubscriptionIdAndHistoryType(
            UUID subscriptionId,
            HoldingHistoryType historyType
    );

    // 같은 관리자 조정 요청의 중복 처리를 방지함
    Optional<HoldingHistory> findByIdempotencyKey(
            String idempotencyKey
    );
}
