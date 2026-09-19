package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionConfirmationManualReviewService {

    public static final String CONFIRMATION_BATCH_FAILURE_CODE =
            "CONFIRMATION_BATCH_FAILED";

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markForManualReview(
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

        Instant manualReviewAt = Instant.now();

        subscription.requireManualReview(
                CONFIRMATION_BATCH_FAILURE_CODE
        );

        subscriptionLifecycleMetrics.publishOutcome(
                subscription,
                SubscriptionLifecycleMetrics.Result.MANUAL_REVIEW,
                manualReviewAt
        );

        subscriptionRepository.flush();

        log.error(
                "청약 확정 자동 처리 실패로 수동 확인 전환. "
                        + "offeringId={}, subscriptionId={}, failureCode={}",
                offeringId,
                subscriptionId,
                CONFIRMATION_BATCH_FAILURE_CODE
        );

        return true;
    }
}
