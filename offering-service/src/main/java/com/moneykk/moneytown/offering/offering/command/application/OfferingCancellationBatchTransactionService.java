package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferingCancellationBatchTransactionService {

    private static final int DEFAULT_CANCELLATION_BATCH_SIZE = 100;

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    @Value("${offering.cancellation.batch-size:100}")
    private int cancellationBatchSize =
            DEFAULT_CANCELLATION_BATCH_SIZE;

    /**
     * 취소 처리 중인 공모 한 건을 선점하고
     * 보상 대상 청약 한 배치를 COMPENSATING으로 전환한다.
     *
     * 공모 선점부터 청약 잠금, 보상 엔티티 생성,
     * Outbox 저장까지 하나의 독립 트랜잭션에서 처리한다.
     *
     * 정상 경로에서는 여러 청약을 한 트랜잭션으로 처리한다.
     * 처리에 실패하면 전체 배치를 롤백하고 실패한 청약 ID를
     * OfferingCancellationBatchException에 담아 전달한다.
     *
     * @return 이번 트랜잭션에서 보상을 시작한 청약 수.
     *         처리 대상 공모가 없으면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int compensateNextBatch() {
        Offering offering =
                offeringRepository
                        .findNextCancellationTargetForUpdate()
                        .orElse(null);

        if (offering == null) {
            return 0;
        }

        List<Subscription> compensationBatch =
                subscriptionRepository
                        .findCompensationBatchForUpdate(
                                offering.getOfferingId(),
                                cancellationBatchSize
                        );

        if (compensationBatch.isEmpty()) {
            log.debug(
                    "공모 취소 보상 대상 청약 배치가 없음. "
                            + "offeringId={}",
                    offering.getOfferingId()
            );

            return 0;
        }

        List<UUID> subscriptionIds =
                compensationBatch.stream()
                        .map(Subscription::getSubscriptionId)
                        .toList();

        /*
         * 하나의 공모 취소 작업에 속한 모든 배치가
         * 같은 추적 값을 사용하도록 offeringId를 사용한다.
         */
        String correlationId =
                offering.getOfferingId().toString();

        CancellationType subscriptionCancellationType =
                OfferingCancellationTypeMapper
                        .toSubscriptionType(offering);

        try {
            for (Subscription subscription : compensationBatch) {
                /*
                 * PROCESSING, HOLD_SUCCEEDED, CONFIRMED
                 * → COMPENSATING
                 */
                subscription.startCompensation(
                        subscriptionCancellationType
                );

                /*
                 * Wallet과 Holding의 보상 진행 상태를 추적할
                 * 보상 엔티티를 생성한다.
                 */
                SubscriptionCompensation compensation =
                        SubscriptionCompensation.create(
                                subscription.getSubscriptionId()
                        );

                subscriptionCompensationRepository.save(
                        compensation
                );

                /*
                 * 청약 상태 변경 및 보상 엔티티 저장과 동일한
                 * 트랜잭션에서 보상 요청 Outbox를 저장한다.
                 */
                subscriptionEventPublisher
                        .publishCompensationRequested(
                                subscription,
                                offering.getAssetId(),
                                correlationId
                        );
            }

            /*
             * save() 시점이 아니라 트랜잭션 커밋 시점에 발생할 수 있는
             * UNIQUE, FK, NOT NULL 등의 DB 오류도 현재 try/catch에서
             * 포착하도록 영속성 컨텍스트를 명시적으로 flush한다.
             *
             * 하나의 EntityManager에 등록된 Subscription 변경,
             * SubscriptionCompensation, Outbox가 함께 flush된다.
             */
            subscriptionCompensationRepository.flush();
        } catch (RuntimeException e) {
            throw new OfferingCancellationBatchException(
                    offering.getOfferingId(),
                    subscriptionIds,
                    e
            );
        }

        log.info(
                "공모 취소 보상 배치 처리 완료. "
                        + "offeringId={}, cancellationType={}, "
                        + "compensationCount={}",
                offering.getOfferingId(),
                offering.getCancellationType(),
                compensationBatch.size()
        );

        return compensationBatch.size();
    }
}
