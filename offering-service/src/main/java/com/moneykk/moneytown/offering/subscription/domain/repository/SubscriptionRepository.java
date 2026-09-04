package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, UUID> {

    // 1. 청약 상세 조회용
    Optional<Subscription> findBySubscriptionIdAndIsDeletedFalse(
            UUID subscriptionId
    );

    // 2. 존재 여부 확인
    // 동일 사용자의 동일 공모 중복 청약 방지용
    boolean existsByOfferingIdAndUserIdAndIsDeletedFalse(
            UUID offeringId,
            UUID userId
    );

    // 공모 삭제 전 청약 이력 존재 여부 확인용
    boolean existsByOfferingId(UUID offeringId);

    /**
     * CANCELLED 공모 상세 조회 시
     * 해당 투자자가 실제 공모 취소 보상 대상이었는지 확인한다.
     *
     * 보상 완료 후 Subscription이 CANCELLED 상태로 전환되고,
     * 공모 취소 사유가 기록된 청약만 관련 투자자로 판단한다.
     */
    boolean existsByOfferingIdAndUserIdAndSubscriptionStatusAndCancellationTypeIsNotNullAndIsDeletedFalse(
            UUID offeringId,
            UUID userId,
            SubscriptionStatus subscriptionStatus
    );

    // 3. 단일 청약 상태 변경을 위한 조회
    /**
     * 공모를 먼저 잠그기 위해 청약의 공모 ID만 조회한다.
     * 청약 엔티티 자체는 이후 잠금 조회로 가져온다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("""
    SELECT s.offeringId
      FROM Subscription s
     WHERE s.subscriptionId = :subscriptionId
       AND s.isDeleted = false
    """)
    Optional<UUID> findOfferingIdBySubscriptionId(
            @Param("subscriptionId") UUID subscriptionId
    );

    /**
     * 청약 상태 변경을 위해 해당 행을 잠금 조회한다.
     * 호출한 트랜잭션이 끝날 때까지 잠금을 유지한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
          FROM Subscription s
         WHERE s.subscriptionId = :subscriptionId
           AND s.isDeleted = false
        """)
    Optional<Subscription> findByIdForUpdate(
            @Param("subscriptionId") UUID subscriptionId
    );


    // 4. 여러 청약 상태 변경을 위한 잠금 조회
    /**
     * 모집 미달 또는 공모 중단 시 보상 대상 청약을 조회한다.
     *
     * PROCESSING, CONFIRMED 상태이면서
     * 삭제되지 않은 청약만 조회한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Subscription> findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
            UUID offeringId,
            List<SubscriptionStatus> subscriptionStatuses
    );

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 조회한다.
     *
     * 장시간 처리 중인 청약의 timeout 처리를 위해
     * Pageable을 사용하여 배치 단위로 조회한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Subscription> findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
            SubscriptionStatus subscriptionStatus,
            Instant reservationExpiresAt,
            Pageable pageable
    );

}