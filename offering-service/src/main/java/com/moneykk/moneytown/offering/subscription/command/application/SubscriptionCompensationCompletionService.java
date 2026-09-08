package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCompensationCompletionService;
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
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;
    private final OfferingCompensationCompletionService offeringCompensationCompletionService;
    private final EntityManager entityManager;

    /**
     * 외부 보상이 모두 완료된 청약의 수량을 복원하고
     * 취소 또는 예약 만료 거절을 완료한다.
     *
     * 결과 상태 저장과 동일한 트랜잭션에서 호출해야 한다.
     * 호출부에서 이미 잠금을 획득했다면 공모 → 청약 → 보상 순서여야 한다.
     *
     * @return 이번 호출에서 최종 상태 전환을 완료했으면 true,
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

        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.CANCELLED
                || subscription.getSubscriptionStatus()
                == SubscriptionStatus.REJECTED) {
            return false;
        }

        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.COMPENSATING) {
            return false;
        }

        boolean reservationExpiration =
                subscription.isReservationExpirationCompensation();

        boolean offeringCancellation =
                subscription.getCancellationType() != null;

        if (!reservationExpiration && !offeringCancellation) {
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

        /*
         * 예약 만료 보상은 공모 자체를 취소하지 않는다.
         * Wallet 보상 성공 후 확보 수량을 복원하고
         * 청약만 REJECTED 상태로 완료한다.
         */
        if (reservationExpiration) {
            if (subscription.isQuantityReserved()) {
                restoreQuantity(
                        offeringId,
                        offering,
                        subscription
                );
            }

            subscription.completeExpirationRejection();

            log.info(
                    "청약 예약 만료 보상 완료. "
                            + "subscriptionId={}, offeringId={}",
                    subscriptionId,
                    offeringId
            );

            return true;
        }

        /*
         * 관리자 중단 또는 모집 미달 보상은
         * CANCELLING 공모에 대해서만 완료할 수 있다.
         */
        if (offering.getOfferingStatus()
                != OfferingStatus.CANCELLING) {
            throw new IllegalStateException(
                    "취소 진행 중인 공모의 청약만 보상을 완료할 수 있습니다. "
                            + "offeringId=" + offeringId
                            + ", status=" + offering.getOfferingStatus()
            );
        }

        if (subscription.isQuantityReserved()) {
            restoreQuantity(
                    offeringId,
                    offering,
                    subscription
            );

            subscription.markCompensationQuantityRestored();
        }

        subscription.completeCancellation(Instant.now());

        offeringCompensationCompletionService.completeIfReady(
                offeringId
        );

        log.info(
                "청약 보상 완료. subscriptionId={}, offeringId={}",
                subscriptionId,
                offeringId
        );

        return true;
    }

    /**
     * 청약이 확보했던 공모 수량을 복원한다.
     *
     * 벌크 UPDATE 결과가 영속성 컨텍스트의 Offering에 반영되도록
     * 복원 성공 후 엔티티를 다시 조회한다.
     */
    private void restoreQuantity(
            UUID offeringId,
            Offering offering,
            Subscription subscription
    ) {
        int restoredRows = offeringRepository.restoreQuantity(
                offeringId,
                subscription.getQuantity(),
                JpaAuditingConfig.SYSTEM_USER_ID
        );

        if (restoredRows != 1) {
            throw new IllegalStateException(
                    "청약 보상에 따른 공모 수량 복원에 실패했습니다. "
                            + "subscriptionId="
                            + subscription.getSubscriptionId()
                            + ", offeringId=" + offeringId
            );
        }

        entityManager.refresh(offering);
    }
}