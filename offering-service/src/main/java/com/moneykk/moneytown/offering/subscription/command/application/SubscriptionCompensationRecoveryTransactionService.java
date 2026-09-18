package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCompensationRecoveryTransactionService {

    private static final String MANUAL_REVIEW_REASON =
            "COMPENSATION_RESULT_TIMEOUT";

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final SubscriptionLifecycleMetrics
            subscriptionLifecycleMetrics;

    /**
     * 일정 시간 이상 완료되지 않은 청약 보상을
     * 수동 확인 대상으로 격리한다.
     *
     * 후보 조회 이후 외부 결과가 도착했을 수 있으므로
     * 독립 트랜잭션에서 최신 상태와 수정 시각을 다시 확인한다.
     *
     * 잠금 순서는 다른 보상 결과 처리와 동일하게
     * Offering → Subscription → SubscriptionCompensation 순서다.
     *
     * @return 이번 호출에서 MANUAL_REVIEW로 전환했으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markStuckForManualReview(
            UUID subscriptionId,
            Instant stuckBefore
    ) {
        Objects.requireNonNull(
                subscriptionId,
                "subscriptionId는 필수입니다."
        );
        Objects.requireNonNull(
                stuckBefore,
                "stuckBefore는 필수입니다."
        );

        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        /*
         * 보상 결과 서비스와 같은 순서로 공모를 먼저 잠근다.
         */
        offeringRepository.findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        /*
         * 다른 인스턴스나 보상 결과 이벤트가 이미 처리했다면
         * 상태를 변경하지 않는다.
         */
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.COMPENSATING) {
            return false;
        }

        SubscriptionCompensation compensation =
                subscriptionCompensationRepository
                        .findBySubscriptionIdForUpdate(subscriptionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "보상 진행 정보가 없습니다. subscriptionId="
                                        + subscriptionId
                        ));

        /*
         * 후보 조회 이후 Wallet과 Holding 보상이 완료됐다면
         * 수동 확인 대상으로 변경하지 않는다.
         */
        if (compensation.isExternalCompensationCompleted()) {
            return false;
        }

        Instant updatedAt = compensation.getUpdatedAt();

        /*
         * 후보 조회 이후 외부 결과가 반영돼 수정 시각이 갱신됐다면
         * 다음 실행에서 다시 판단한다.
         */
        if (updatedAt == null || updatedAt.isAfter(stuckBefore)) {
            return false;
        }

        subscription.requireManualReview(
                MANUAL_REVIEW_REASON
        );

        subscriptionLifecycleMetrics.publishOutcome(
                subscription,
                SubscriptionLifecycleMetrics.Result.MANUAL_REVIEW,
                Instant.now()
        );

        log.error(
                "외부 보상 결과 장기 미완료로 수동 확인 전환. "
                        + "subscriptionId={}, offeringId={}, "
                        + "walletStatus={}, holdingStatus={}, "
                        + "compensationUpdatedAt={}",
                subscriptionId,
                offeringId,
                compensation.getWalletStatus(),
                compensation.getHoldingStatus(),
                updatedAt
        );

        return true;
    }
}