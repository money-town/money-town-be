package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionTimeoutBatchTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    /**
     * 만료된 PROCESSING 청약을 가진 공모 한 건과 해당 청약 한 배치를
     * 선점하여 보상 처리를 시작한다.
     *
     * 공모 선점, 청약 잠금, 상태 변경, 보상 엔티티 및 Outbox 저장을
     * 하나의 독립 트랜잭션에서 처리한다. 여러 인스턴스에서는
     * 공모 단위 SKIP LOCKED로 서로 다른 공모를 처리한다.
     *
     * @return 실제로 보상을 시작한 청약 수. 대상이 없으면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processNextBatch(
            Instant now,
            int batchSize
    ) {
        Offering offering = offeringRepository
                .findNextExpiredReservationTargetForUpdate(now)
                .orElse(null);

        if (offering == null) {
            return 0;
        }

        List<Subscription> expiredBatch = subscriptionRepository
                .findExpiredProcessingBatchForUpdate(
                        offering.getOfferingId(),
                        now,
                        batchSize
                );

        if (expiredBatch.isEmpty()) {
            return 0;
        }

        List<UUID> subscriptionIds = expiredBatch.stream()
                .map(Subscription::getSubscriptionId)
                .toList();

        String correlationId = UUID.randomUUID().toString();

        try {
            for (Subscription subscription : expiredBatch) {
                subscription.startExpirationCompensation(now);

                SubscriptionCompensation compensation =
                        SubscriptionCompensation
                                .createForReservationExpiration(
                                        subscription.getSubscriptionId()
                                );

                subscriptionCompensationRepository.save(compensation);

                subscriptionEventPublisher
                        .publishCompensationRequested(
                                subscription,
                                offering.getAssetId(),
                                correlationId
                        );
            }

            subscriptionCompensationRepository.flush();
        } catch (RuntimeException e) {
            throw new SubscriptionTimeoutBatchException(
                    offering.getOfferingId(),
                    subscriptionIds,
                    e
            );
        }

        return expiredBatch.size();
    }
}
