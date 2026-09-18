package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferingCancellationManualReviewService {

    public static final String CANCELLATION_BATCH_FAILURE_CODE =
            "CANCELLATION_BATCH_FAILED";

    private static final Set<SubscriptionStatus>
            COMPENSATABLE_STATUSES = Set.of(
            SubscriptionStatus.PROCESSING,
            SubscriptionStatus.HOLD_SUCCEEDED,
            SubscriptionStatus.CONFIRMED
    );

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 관리자 공모 중단 보상을 자동으로 시작하지 못한 청약을
     * 수동 확인 대상으로 격리한다.
     *
     * 공모 중단 취소 유형을 먼저 기록한 뒤 MANUAL_REVIEW로
     * 전환하므로 관리자가 이후 보상 재처리를 시작할 수 있다.
     *
     * 이미 처리됐거나 대상 상태가 아니면 false를 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markForManualReview(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(
                        () -> new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );

        if (offering.getOfferingStatus()
                != OfferingStatus.CANCELLING
                || offering.getCancellationType()
                != com.moneykk.moneytown.offering.offering.domain.entity
                .CancellationType.ADMIN_CANCELLED) {
            return false;
        }

        Subscription subscription =
                subscriptionRepository
                        .findByIdForUpdate(subscriptionId)
                        .orElse(null);

        if (subscription == null) {
            return false;
        }

        if (!offeringId.equals(
                subscription.getOfferingId()
        )) {
            return false;
        }

        if (!COMPENSATABLE_STATUSES.contains(
                subscription.getSubscriptionStatus()
        )) {
            return false;
        }

        /*
         * cancellationType을 보존하기 위해 먼저 공모 중단 보상 상태로
         * 전환한 뒤 MANUAL_REVIEW로 격리한다.
         */
        subscription.startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        subscription.requireManualReview(
                CANCELLATION_BATCH_FAILURE_CODE
        );

        log.error(
                "관리자 공모 중단 보상 자동 처리 실패로 수동 확인 전환. "
                        + "offeringId={}, subscriptionId={}, failureCode={}",
                offeringId,
                subscriptionId,
                CANCELLATION_BATCH_FAILURE_CODE
        );

        return true;
    }
}