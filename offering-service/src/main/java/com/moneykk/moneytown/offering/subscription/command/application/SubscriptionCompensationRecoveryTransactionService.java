package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
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
import java.util.List;
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
     * 장기 미완료 보상을 가진 공모 한 건과 청약 한 배치를 선점하여
     * MANUAL_REVIEW로 전환한다.
     *
     * 공모 선점부터 청약·보상 잠금과 상태 변경까지 하나의 독립
     * 트랜잭션에서 처리한다. 여러 인스턴스에서는 공모 단위
     * SKIP LOCKED로 서로 다른 공모를 처리한다.
     *
     * @return 이번 트랜잭션에서 수동 확인으로 전환한 청약 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markNextStuckBatchForManualReview(
            Instant stuckBefore,
            int batchSize
    ) {
        Objects.requireNonNull(
                stuckBefore,
                "stuckBefore는 필수입니다."
        );

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize는 1 이상이어야 합니다."
            );
        }

        Offering offering = offeringRepository
                .findNextStuckCompensationTargetForUpdate(
                        stuckBefore
                )
                .orElse(null);

        if (offering == null) {
            return 0;
        }

        List<Subscription> subscriptions = subscriptionRepository
                .findStuckCompensationBatchForUpdate(
                        offering.getOfferingId(),
                        stuckBefore,
                        batchSize
                );

        if (subscriptions.isEmpty()) {
            return 0;
        }

        List<UUID> subscriptionIds = subscriptions.stream()
                .map(Subscription::getSubscriptionId)
                .toList();

        int manualReviewCount = 0;

        try {
            for (Subscription subscription : subscriptions) {
                SubscriptionCompensation compensation =
                        subscriptionCompensationRepository
                                .findBySubscriptionIdForUpdate(
                                        subscription
                                                .getSubscriptionId()
                                )
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "보상 진행 정보가 없습니다. "
                                                        + "subscriptionId="
                                                        + subscription
                                                        .getSubscriptionId()
                                        )
                                );

                if (markLockedForManualReview(
                        subscription,
                        compensation,
                        offering.getOfferingId(),
                        stuckBefore
                )) {
                    manualReviewCount++;
                }
            }

            subscriptionRepository.flush();
        } catch (RuntimeException e) {
            throw new SubscriptionCompensationRecoveryBatchException(
                    offering.getOfferingId(),
                    subscriptionIds,
                    e
            );
        }

        return manualReviewCount;
    }

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

        return markLockedForManualReview(
                subscription,
                compensation,
                offeringId,
                stuckBefore
        );
    }

    /**
     * 잠금이 확보된 청약과 보상 진행 정보를 최신 상태로 재검증한 뒤
     * 수동 확인 대상으로 전환한다.
     */
    private boolean markLockedForManualReview(
            Subscription subscription,
            SubscriptionCompensation compensation,
            UUID offeringId,
            Instant stuckBefore
    ) {
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.COMPENSATING) {
            return false;
        }

        if (compensation.isExternalCompensationCompleted()) {
            return false;
        }

        Instant updatedAt = compensation.getUpdatedAt();

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
                subscription.getSubscriptionId(),
                offeringId,
                compensation.getWalletStatus(),
                compensation.getHoldingStatus(),
                updatedAt
        );

        return true;
    }
}
