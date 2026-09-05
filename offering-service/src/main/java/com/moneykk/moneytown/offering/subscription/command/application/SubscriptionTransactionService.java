package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 실제 청약 데이터 변경을 하나의 Local Transaction으로 처리한다.
 *
 * 외부 HTTP 호출(User / FDS)은 이 서비스 밖에서 수행하고,
 * 실제 DB 변경이 필요한 구간에서만 트랜잭션을 시작한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;

    private final SubscriptionEventPublisher subscriptionEventPublisher;

    @Value("${subscription.reservation-timeout-minutes:10}")
    private long reservationTimeoutMinutes;

    /**
     * 청약 수량 확보부터 Subscription 생성,
     * 멱등 요청 완료 처리까지 동일 트랜잭션에서 처리한다.
     */
    @Transactional
    public SubscriptionCreateResponse createSubscription(
            UUID offeringId,
            UUID userId,
            String idempotencyKey,
            Long quantity,
            Long pricePerUnit,
            String correlationId
    ) {
        validateDuplicateSubscription(
                offeringId,
                userId
        );

        int updatedRows = offeringRepository.reserveQuantity(
                offeringId,
                quantity,
                userId
        );

        if (updatedRows == 0) {
            throw new BusinessException(
                    SubscriptionErrorCode.INSUFFICIENT_REMAINING_QUANTITY
            );
        }

        Instant reservationExpiresAt =
                Instant.now().plus(
                        reservationTimeoutMinutes,
                        ChronoUnit.MINUTES
                );

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                quantity,
                pricePerUnit,
                reservationExpiresAt
        );

        Subscription savedSubscription =
                subscriptionRepository.save(subscription);

        subscriptionEventPublisher.publishReserved(
                savedSubscription,
                correlationId
        );

        int completed = idempotencyRequestRepository.complete(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                savedSubscription.getSubscriptionId(),
                HttpStatus.ACCEPTED.value()
        );

        if (completed != 1) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_COMPLETION_FAILED
            );
        }

        return SubscriptionCreateResponse.from(savedSubscription);
    }

    /**
     * 동일 사용자가 동일 공모에 이미 청약했는지 확인한다.
     */
    private void validateDuplicateSubscription(
            UUID offeringId,
            UUID userId
    ) {
        boolean exists =
                subscriptionRepository
                        .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                                offeringId,
                                userId
                        );

        if (exists) {
            throw new BusinessException(
                    SubscriptionErrorCode.DUPLICATE_SUBSCRIPTION
            );
        }
    }
}