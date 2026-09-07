package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
}