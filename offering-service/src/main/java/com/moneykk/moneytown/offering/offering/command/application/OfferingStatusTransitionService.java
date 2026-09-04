package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingStatusTransitionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;

    private static final int TRANSITION_BATCH_SIZE = 100;

    private static final List<SubscriptionStatus> COMPENSATABLE_STATUSES =
            List.of(
                    SubscriptionStatus.PROCESSING,
                    SubscriptionStatus.CONFIRMED
            );

    // 시작 시간이 도래한 SCHEDULED 공모를 OPEN으로 일괄 전환한다.
    @Transactional
    public int openScheduledOfferings() {
        return offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }

    // 모집 종료 시간이 도래한 SOLD_OUT 공모를 CLOSED로 일괄 전환한다.
    @Transactional
    public int closeSoldOutOfferings() {
        return offeringRepository.closeSoldOutOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }

    /**
     * 모집 종료 시간이 도래했지만 잔여 수량이 남아 있는 OPEN 공모를
     * 모집 미달에 따른 CANCELLING 상태로 전환한다.
     *
     * 해당 공모의 PROCESSING, CONFIRMED 청약은
     * 보상 처리를 위해 COMPENSATING 상태로 전환한다.
     *
     * 한 번에 최대 100건의 공모를 조회하여 처리하며,
     * 실제 상태 전환 규칙은 각 도메인에서 검증한다.
     *
     * @return CANCELLING 전환 대상 공모 수
     */
    @Transactional
    public int startUnderSubscribedCancellations() {

        List<Offering> offerings =
                offeringRepository.findUnderSubscribedOfferingsForUpdate(
                        Instant.now(),
                        PageRequest.of(0, TRANSITION_BATCH_SIZE)
                );

        for (Offering offering : offerings) {

            offering.startUnderSubscribedCancellation();

            /*
             * 스케줄러에서 시작한 작업이므로 Gateway 요청 ID가 없다.
             * 공모별 보상 작업의 추적 ID를 생성하고,
             * 해당 공모의 청약별 이벤트에 동일하게 전달한다.
             */
            String correlationId = UUID.randomUUID().toString();


            List<Subscription> subscriptions =
                    subscriptionRepository
                            .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                                    offering.getOfferingId(),
                                    COMPENSATABLE_STATUSES
                            );

            for (Subscription subscription : subscriptions) {
                subscription.startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

                SubscriptionCompensation compensation =
                        SubscriptionCompensation.create(
                                subscription.getSubscriptionId()
                        );

                subscriptionCompensationRepository.save(compensation);

                subscriptionEventPublisher.publishCompensationRequested(
                        subscription,
                        offering.getAssetId(),
                        correlationId
                );
            }
        }

        return offerings.size();
    }
}