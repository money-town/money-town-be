package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingCancellationItemTransactionService {

    private static final Set<SubscriptionStatus>
            COMPENSATABLE_STATUSES = Set.of(
            SubscriptionStatus.PROCESSING,
            SubscriptionStatus.HOLD_SUCCEEDED,
            SubscriptionStatus.CONFIRMED
    );

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    /**
     * 배치 처리에 실패한 청약 한 건의 보상을
     * 독립된 트랜잭션에서 시작한다.
     *
     * 이미 다른 작업에서 처리됐거나 더 이상 처리 대상이 아니면
     * 예외 없이 false를 반환한다.
     *
     * 처리 도중 예외가 발생하면 해당 청약 한 건의 상태 변경,
     * 보상 엔티티와 Outbox 저장만 롤백된다.
     *
     * @return 이번 호출에서 보상을 시작했으면 true,
     *         이미 처리됐거나 대상이 아니면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean compensate(
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

        /*
         * 관리자 공모 중단 작업이 이미 끝났거나
         * 다른 취소 유형이면 현재 건별 복구 작업에서 처리하지 않는다.
         */
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

        /*
         * 예외에 포함된 subscriptionId가 해당 공모의 청약인지
         * 트랜잭션 안에서 다시 검증한다.
         */
        if (!offeringId.equals(
                subscription.getOfferingId()
        )) {
            return false;
        }

        /*
         * 정상 배치가 일부 처리됐거나 다른 이벤트에서 상태를
         * 변경했을 수 있으므로 현재 상태를 다시 확인한다.
         */
        if (!COMPENSATABLE_STATUSES.contains(
                subscription.getSubscriptionStatus()
        )) {
            return false;
        }

        subscription.startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscription.getSubscriptionId()
                );

        subscriptionCompensationRepository.save(
                compensation
        );

        subscriptionEventPublisher
                .publishCompensationRequested(
                        subscription,
                        offering.getAssetId(),
                        offeringId.toString()
                );

        /*
         * 커밋 시점 DB 오류를 현재 건별 트랜잭션 안에서 발생시킨다.
         */
        subscriptionCompensationRepository.flush();

        return true;
    }
}