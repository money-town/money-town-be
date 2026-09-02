package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

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
}