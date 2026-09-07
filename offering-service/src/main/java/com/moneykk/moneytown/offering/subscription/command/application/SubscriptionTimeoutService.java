package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
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
public class SubscriptionTimeoutService {

    private static final int TIMEOUT_BATCH_SIZE = 100;

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 조회하여
     * COMPENSATING 상태로 전환하고 Wallet 보상 요청을 저장한다.
     *
     * 청약 상태 변경, 보상 진행 정보 생성 및 Outbox 저장은
     * 동일한 로컬 트랜잭션에서 처리한다.
     *
     * TODO: 대량 타임아웃 발생 시
     * 배치 크기 및 반복 처리 방식의 성능을 검증한다.
     *
     * @return timeout 처리된 청약 수
     */
    @Transactional
    public int processExpiredReservations() {

        Instant now = Instant.now();

        List<Subscription> subscriptions =
                subscriptionRepository
                        .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                                SubscriptionStatus.PROCESSING,
                                now,
                                PageRequest.of(0, TIMEOUT_BATCH_SIZE)
                        );

        for (Subscription subscription : subscriptions) {
            Offering offering = offeringRepository
                    .findByOfferingIdAndIsDeletedFalse(
                            subscription.getOfferingId()
                    )
                    .orElseThrow(() -> new BusinessException(
                            OfferingErrorCode.OFFERING_NOT_FOUND
                    ));

            subscription.startExpirationCompensation(now);

            SubscriptionCompensation compensation =
                    SubscriptionCompensation
                            .createForReservationExpiration(
                                    subscription.getSubscriptionId()
                            );

            subscriptionCompensationRepository.save(compensation);

            subscriptionEventPublisher.publishCompensationRequested(
                    subscription,
                    offering.getAssetId(),
                    UUID.randomUUID().toString()
            );
        }
        return subscriptions.size();
    }
}
