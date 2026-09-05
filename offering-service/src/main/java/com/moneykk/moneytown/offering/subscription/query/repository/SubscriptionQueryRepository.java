package com.moneykk.moneytown.offering.subscription.query.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionQueryRepository {

    Page<Subscription> searchMySubscriptions(
            UUID userId,
            SubscriptionSearchCondition condition,
            Pageable pageable
    );
}