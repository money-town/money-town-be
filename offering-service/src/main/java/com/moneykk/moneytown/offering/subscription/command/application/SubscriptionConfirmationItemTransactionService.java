package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionConfirmationItemTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirm(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        boolean finalizableOffering =
                (
                        offering.getOfferingStatus()
                                == OfferingStatus.SOLD_OUT
                                || offering.getOfferingStatus()
                                == OfferingStatus.CLOSED
                )
                        && offering.getRemainingQuantity() == 0L;

        if (!finalizableOffering) {
            return false;
        }

        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElse(null);

        if (subscription == null
                || !offeringId.equals(subscription.getOfferingId())
                || subscription.getSubscriptionStatus()
                != SubscriptionStatus.HOLD_SUCCEEDED
                || !subscription.isQuantityReserved()) {
            return false;
        }

        Instant confirmedAt = Instant.now();

        subscription.confirm(confirmedAt);

        subscriptionEventPublisher.publishConfirmed(
                subscription,
                offering.getAssetId(),
                offeringId.toString()
        );

        subscriptionLifecycleMetrics.publishOutcome(
                subscription,
                SubscriptionLifecycleMetrics.Result.CONFIRMED,
                confirmedAt
        );

        subscriptionRepository.flush();

        return true;
    }
}
