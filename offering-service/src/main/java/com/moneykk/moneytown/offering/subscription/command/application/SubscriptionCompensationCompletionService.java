package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
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
public class SubscriptionCompensationCompletionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final EntityManager entityManager;

    /**
     * 외부 보상이 모두 완료된 청약의 수량을 복원하고 취소를 완료한다.
     *
     * 결과 상태 저장과 동일한 트랜잭션에서 호출해야 한다.
     * 호출부에서 이미 잠금을 획득했다면 공모 → 청약 → 보상 순서여야 한다.
     *
     * @return 이번 호출에서 취소를 완료했으면 true,
     *         이미 완료됐거나 자동 완료 대상이 아니면 false
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completeIfReady(UUID subscriptionId) {
        Objects.requireNonNull(
                subscriptionId,
                "subscriptionId는 필수입니다."
        );

        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        // 이미 취소된 청약의 수량과 취소 시각을 다시 변경하지 않는다.
        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.CANCELLED) {
            return false;
        }

        // MANUAL_REVIEW 및 공모 취소 사유가 없는 타임아웃 청약은 제외한다.
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.COMPENSATING
                || subscription.getCancellationType() == null) {
            return false;
        }

        SubscriptionCompensation compensation =
                subscriptionCompensationRepository
                        .findBySubscriptionIdForUpdate(subscriptionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "보상 진행 정보가 없습니다. subscriptionId="
                                        + subscriptionId
                        ));

        if (!compensation.isExternalCompensationCompleted()) {
            return false;
        }

        if (offering.getOfferingStatus() != OfferingStatus.CANCELLING) {
            throw new IllegalStateException(
                    "취소 진행 중인 공모의 청약만 보상을 완료할 수 있습니다. "
                            + "offeringId=" + offeringId
                            + ", status=" + offering.getOfferingStatus()
            );
        }

        if (subscription.isQuantityReserved()) {
            int restoredRows = offeringRepository.restoreQuantity(
                    offeringId,
                    subscription.getQuantity(),
                    JpaAuditingConfig.SYSTEM_USER_ID
            );

            if (restoredRows != 1) {
                throw new IllegalStateException(
                        "청약 보상에 따른 공모 수량 복원에 실패했습니다. "
                                + "subscriptionId=" + subscriptionId
                                + ", offeringId=" + offeringId
                );
            }

            /*
             * 벌크 UPDATE는 관리 엔티티에 반영되지 않으므로 갱신한다.
             * 이후 공모 상태 변경 시 이전 잔여 수량으로 덮어쓰는 것을 방지한다.
             */
            entityManager.refresh(offering);

            subscription.markCompensationQuantityRestored();
        }

        subscription.completeCancellation(Instant.now());

        log.info(
                "청약 보상 완료. subscriptionId={}, offeringId={}",
                subscriptionId,
                offeringId
        );

        return true;
    }
}