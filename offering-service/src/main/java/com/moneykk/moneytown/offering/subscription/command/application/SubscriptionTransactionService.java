package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String DUPLICATE_SUBSCRIPTION_CONSTRAINT = "uq_subscriptions_offering_user";

    private final SubscriptionRepository subscriptionRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;

    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final OfferingQuantityReservationService offeringQuantityReservationService;

    @Value("${subscription.reservation-timeout-minutes:10}")
    private long reservationTimeoutMinutes;

    /**
     * 청약 수량 확보부터 Subscription 생성,
     * 멱등 요청 완료 처리까지 처리한다.
     *
     * 수량 확보(reserve)는 offering 행 락을 최소한으로 짧게 유지하기 위해
     * OfferingQuantityReservationService에서 별도의 REQUIRES_NEW 트랜잭션으로 즉시
     * 커밋한다. 이 메서드 자체의 트랜잭션(이 메서드 이후 구간)은 그와 독립적으로,
     * Subscription 저장 · 이벤트 발행 · 멱등 완료 처리만 묶는다.
     *
     * 확보 이후 단계가 실패하면 이미 커밋된 확보 수량을 release()로 되돌린 뒤
     * 원래 예외를 그대로 전달한다.
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

        int updatedRows = offeringQuantityReservationService.reserve(
                offeringId,
                quantity,
                userId
        );

        if (updatedRows == 0) {
            throw new BusinessException(
                    SubscriptionErrorCode.INSUFFICIENT_REMAINING_QUANTITY
            );
        }

        try {
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
                    saveSubscription(subscription);

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

        } catch (RuntimeException e) {
            /*
             * reserve()가 이미 별도 트랜잭션으로 커밋되어 있어
             * 여기서 실패해도 자동으로 되돌아가지 않는다.
             * 확보했던 수량을 명시적으로 복원한다.
             */
            offeringQuantityReservationService.release(
                    offeringId,
                    quantity
            );

            throw e;
        }
    }

    /**
     * 청약을 즉시 INSERT하여 동시 중복 청약의 UNIQUE 제약 위반을 확인한다.
     *
     * 동일 공모·사용자 중복만 DUPLICATE_SUBSCRIPTION으로 변환하고,
     * 다른 DB 제약 위반은 원래 예외를 그대로 전달한다.
     */
    private Subscription saveSubscription(
            Subscription subscription
    ) {
        try {
            return subscriptionRepository.saveAndFlush(subscription);

        } catch (DataIntegrityViolationException e) {
            String constraintName = extractConstraintName(e);

            if (DUPLICATE_SUBSCRIPTION_CONSTRAINT.equals(
                    constraintName
            )) {
                throw new BusinessException(
                        SubscriptionErrorCode.DUPLICATE_SUBSCRIPTION
                );
            }

            throw e;
        }
    }

    private String extractConstraintName(
            DataIntegrityViolationException exception
    ) {
        return exception.getCause()
                instanceof ConstraintViolationException constraintViolation
                ? constraintViolation.getConstraintName()
                : null;
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