package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.HoldingSubscriptionState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HoldingSubscriptionStateRepository
        extends JpaRepository<HoldingSubscriptionState, UUID> {

    // 동시에 처음 들어온 배정·회수 요청 중 하나만 상태 행을 생성함
    @Modifying
    @Query(value = """
            INSERT INTO p_holding_subscription_states
                (subscription_id, status, created_at, updated_at)
            VALUES (:subscriptionId, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (subscription_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("subscriptionId") UUID subscriptionId);

    // 같은 청약의 배정과 회수를 순서대로 처리함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT state
            FROM HoldingSubscriptionState state
            WHERE state.subscriptionId = :subscriptionId
            """)
    Optional<HoldingSubscriptionState> findByIdForUpdate(
            @Param("subscriptionId") UUID subscriptionId
    );
}
