package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.domain.repository.projection.UnderSubscribedOfferingTarget;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferingStatusTransitionService {

    private static final int TRANSITION_BATCH_SIZE = 100;

    private static final List<SubscriptionStatus>
            COMPENSATABLE_STATUSES = List.of(
            SubscriptionStatus.PROCESSING,
            SubscriptionStatus.HOLD_SUCCEEDED,
            SubscriptionStatus.CONFIRMED
    );

    private final OfferingRepository offeringRepository;

    /*
     * 관리자 긴급 중단에서 사용하는 기존 의존성입니다.
     */
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;
    private final OfferingCompensationCompletionService offeringCompensationCompletionService;

    /*
     * 모집 미달 공모 한 건을 독립 트랜잭션으로 처리한다.
     */
    private final OfferingUnderSubscribedTransactionService offeringUnderSubscribedTransactionService;

    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /**
     * 시작 시간이 도래한 SCHEDULED 공모를 OPEN으로 일괄 전환한다.
     */
    @Transactional
    public int openScheduledOfferings() {
        return offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }

    /**
     * 모집 종료 시간이 도래한 SOLD_OUT 공모를 CLOSED로 일괄 전환한다.
     */
    @Transactional
    public int closeSoldOutOfferings() {
        return offeringRepository.closeSoldOutOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }

    /**
     * 관리자 요청으로 공모 중단 및 청약 보상을 시작한다.
     *
     * 기존 관리자 긴급 중단 API에서 사용하므로 유지해야 한다.
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
                .orElseThrow(
                        () -> new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );

        offering.startAdminCancellation();

        startSubscriptionCompensations(
                offering,
                CancellationType.OFFERING_ADMIN_CANCELLED,
                correlationId
        );

        offeringCompensationCompletionService.completeIfReady(
                offeringId
        );

        return OfferingCancellationResponse.from(offering);
    }

    /**
     * 모집 종료 시간이 도래했지만 잔여 수량이 남은 공모를
     * 키셋 방식으로 100건씩 조회하여 모집 미달 취소를 시작한다.
     *
     * 공모별 처리는 독립 트랜잭션에서 수행한다. 특정 공모가 실패해
     * 조회 조건에 남더라도 같은 실행에서 후속 공모 처리를 계속한다.
     *
     * @return 실제로 모집 미달 취소 처리를 시작한 공모 수
     */
    public int startUnderSubscribedCancellations() {

        Instant now = Instant.now();

        Instant lastEndAt = null;
        UUID lastOfferingId = null;

        int processedCount = 0;

        while (true) {
            List<UnderSubscribedOfferingTarget> targets;

            if (lastEndAt == null) {
                targets =
                        offeringRepository
                                .findUnderSubscribedOfferingTargets(
                                        now,
                                        PageRequest.of(
                                                0,
                                                TRANSITION_BATCH_SIZE
                                        )
                                );
            } else {
                targets =
                        offeringRepository
                                .findUnderSubscribedOfferingTargetsAfter(
                                        now,
                                        lastEndAt,
                                        lastOfferingId,
                                        PageRequest.of(
                                                0,
                                                TRANSITION_BATCH_SIZE
                                        )
                                );
            }

            if (targets.isEmpty()) {
                break;
            }

            for (UnderSubscribedOfferingTarget target : targets) {
                try {
                    boolean processed =
                            offeringUnderSubscribedTransactionService
                                    .startUnderSubscribedCancellation(
                                            target.offeringId(),
                                            now
                                    );

                    if (processed) {
                        processedCount++;
                    }
                } catch (Exception e) {
                    offeringSchedulerMetrics
                            .recordUnderSubscribedItemFailure();

                    /*
                     * 현재 대상이 실패해도 키셋 커서를 전진시켜
                     * 같은 실행에서 후속 공모 처리를 계속한다.
                     */
                    log.error(
                            "모집 미달 공모 취소 처리 실패. offeringId={}",
                            target.offeringId(),
                            e
                    );
                }
            }

            UnderSubscribedOfferingTarget lastTarget =
                    targets.get(targets.size() - 1);

            lastEndAt = lastTarget.endAt();
            lastOfferingId = lastTarget.offeringId();

            if (targets.size() < TRANSITION_BATCH_SIZE) {
                break;
            }
        }

        return processedCount;
    }

    /**
     * 관리자 공모 중단 시 보상 대상 청약을 COMPENSATING으로
     * 전환하고 보상 요청 이벤트를 저장한다.
     *
     * 모집 미달 스케줄러 로직은 신규 Transaction Service로
     * 이동했지만 관리자 긴급 중단에서 사용하므로 유지한다.
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