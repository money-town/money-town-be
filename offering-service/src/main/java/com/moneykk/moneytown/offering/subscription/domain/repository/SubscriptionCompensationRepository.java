package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionCompensationRepository
        extends JpaRepository<SubscriptionCompensation, UUID> {

    /**
     * 청약 ID로 보상 진행 정보를 조회한다.
     * 상태 변경에는 잠금 조회 메서드를 사용한다.
     */
    Optional<SubscriptionCompensation> findBySubscriptionId(
            UUID subscriptionId
    );

    /**
     * 보상 결과 반영을 위해 해당 행을 잠금 조회한다.
     *
     * Wallet과 Holding 결과가 동시에 도착해도
     * 같은 보상 행의 변경을 순서대로 처리한다.
     * 호출한 트랜잭션이 끝날 때까지 잠금을 유지한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
          FROM SubscriptionCompensation c
         WHERE c.subscriptionId = :subscriptionId
        """)
    Optional<SubscriptionCompensation> findBySubscriptionIdForUpdate(
            @Param("subscriptionId") UUID subscriptionId
    );

    /**
     * Wallet 또는 Holding 보상이 완료되지 않은 채
     * 일정 시간 이상 COMPENSATING에 머문 청약 수를 조회한다.
     */
    @Query(
            value = """
                SELECT COUNT(*)
                  FROM p_subscription_compensations c
                  JOIN p_subscriptions s
                    ON s.subscription_id = c.subscription_id
                 WHERE s.subscription_status = 'COMPENSATING'
                   AND s.is_deleted = false
                   AND c.updated_at <= :stuckBefore
                   AND (
                       c.wallet_status <> 'SUCCEEDED'
                       OR c.holding_status <> 'SUCCEEDED'
                   )
                """,
            nativeQuery = true
    )
    long countStuckCompensations(
            @Param("stuckBefore") Instant stuckBefore
    );

    /**
     * 일정 시간 이상 외부 보상 결과가 완료되지 않은
     * COMPENSATING 청약 ID를 제한된 개수만 조회한다.
     *
     * 이 조회는 후보만 선별한다.
     * 실제 상태 변경 서비스에서는
     * Offering → Subscription → SubscriptionCompensation 순서로
     * 다시 잠그고 최신 상태를 검증해야 한다.
     */
    @Query(
            value = """
            SELECT c.subscription_id
              FROM p_subscription_compensations c
              JOIN p_subscriptions s
                ON s.subscription_id = c.subscription_id
             WHERE s.subscription_status = 'COMPENSATING'
               AND s.is_deleted = FALSE
               AND c.updated_at <= :stuckBefore
               AND (
                   c.wallet_status <> 'SUCCEEDED'
                   OR c.holding_status <> 'SUCCEEDED'
               )
             ORDER BY c.updated_at ASC,
                      c.subscription_id ASC
             LIMIT :batchSize
            """,
            nativeQuery = true
    )
    List<UUID> findStuckCompensationSubscriptionIds(
            @Param("stuckBefore") Instant stuckBefore,
            @Param("batchSize") int batchSize
    );
}