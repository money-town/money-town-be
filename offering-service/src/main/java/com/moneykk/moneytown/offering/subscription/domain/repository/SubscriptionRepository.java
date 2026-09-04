package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, UUID> {

    // 청약 상세 조회용
    Optional<Subscription> findBySubscriptionIdAndIsDeletedFalse(
            UUID subscriptionId
    );

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

    /**
     * 모집 미달 또는 공모 중단 시 보상 대상 청약을 조회한다.
     *
     * PROCESSING, CONFIRMED 상태이면서
     * 삭제되지 않은 청약만 조회한다.
     */
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
    List<Subscription> findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
            SubscriptionStatus subscriptionStatus,
            Instant reservationExpiresAt,
            Pageable pageable
    );

}