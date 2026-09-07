package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
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
    private final OfferingCompensationCompletionService offeringCompensationCompletionService;

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
     * 관리자 요청으로 공모 중단 및 청약 보상을 시작한다.
     *
     * 공모를 먼저 잠근 뒤 보상 대상 청약을 잠금 조회하여
     * 전체 처리 과정의 잠금 순서를 공모 → 청약으로 유지한다.
     *
     * 보상 대상 또는 미해결 청약이 없으면 같은 트랜잭션에서
     * 공모 취소가 완료될 수 있다.
     */
    @Transactional
    public OfferingCancellationResponse cancelByAdmin(
            UUID offeringId,
            String correlationId
    ) {
        validateAdminCancellationInput(
                offeringId,
                correlationId
        );

        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        offering.startAdminCancellation();

        startSubscriptionCompensations(
                offering,
                CancellationType.OFFERING_ADMIN_CANCELLED,
                correlationId
        );

        /*
         * SCHEDULED 공모 또는 보상 대상과 미해결 청약이 없는 공모는
         * 같은 트랜잭션에서 바로 CANCELLED로 전환될 수 있다.
         *
         * 미해결 청약이 있으면 CANCELLING 상태를 유지한다.
         */
        offeringCompensationCompletionService.completeIfReady(
                offeringId
        );

        return OfferingCancellationResponse.from(offering);
    }


    /**
     * 모집 종료 시점에 잔여 수량이 있거나,
     * 모집 종료 후 Wallet 동결 실패로 수량이 복원된 공모를
     * 모집 미달에 따른 CANCELLING 상태로 전환한다.
     *
     * 조회 대상에는 OPEN 공모와,
     * 늦은 수량 복원으로 잔여 수량이 생긴
     * SOLD_OUT, CLOSED 공모가 포함된다.
     *
     * 해당 공모의 PROCESSING, CONFIRMED 청약은
     * 보상 처리를 위해 COMPENSATING 상태로 전환한다.
     *
     * 한 번에 최대 100건의 공모를 조회하여 처리하며,
     * 실제 상태 전환 규칙은 각 도메인에서 검증한다.
     *
     * @return 취소 처리를 시작한 공모 수
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

            startSubscriptionCompensations(
                    offering,
                    CancellationType.OFFERING_UNDER_SUBSCRIBED,
                    correlationId
            );

            // 청약이 없거나 모든 청약이 이미 해결된 공모도 완료 여부를 확인한다.
            offeringCompensationCompletionService.completeIfReady(
                    offering.getOfferingId()
            );
        }

        return offerings.size();
    }

    /**
     * 공모 취소 유형에 따라 보상 대상 청약을 전환하고
     * 보상 요청 이벤트를 Outbox에 저장한다.
     */
    /*
     * TODO: Asset/Holding Kafka 이벤트 연동 후 통합 검증
     * - SubscriptionConfirmed 소비 및 배정 결과 발행
     * - SubscriptionCompensationRequested 소비 및 회수 결과 발행
     * - HOLDING_ALLOCATION_BLOCKED 실패 결과 수신
     * - NO_ACTION의 NOT_ALLOCATED / ALREADY_REVOKED 계약 확인
     */
    private void startSubscriptionCompensations(
            Offering offering,
            CancellationType cancellationType,
            String correlationId
    ) {
        List<Subscription> subscriptions =
                subscriptionRepository
                        .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                                offering.getOfferingId(),
                                COMPENSATABLE_STATUSES
                        );

        for (Subscription subscription : subscriptions) {
            subscription.startCompensation(
                    cancellationType
            );

            SubscriptionCompensation compensation =
                    SubscriptionCompensation.create(
                            subscription.getSubscriptionId()
                    );

            subscriptionCompensationRepository.save(
                    compensation
            );

            subscriptionEventPublisher.publishCompensationRequested(
                    subscription,
                    offering.getAssetId(),
                    correlationId
            );
        }
    }

    private void validateAdminCancellationInput(
            UUID offeringId,
            String correlationId
    ) {
        if (offeringId == null
                || correlationId == null
                || correlationId.isBlank()) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }
    }
}