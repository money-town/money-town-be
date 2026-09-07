package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 청약 한도 초과 처리 결과를 별도 트랜잭션으로 기록한다.
 *
 * 청약 엔티티가 생성되기 전에 발생하는 실패이므로
 * 멱등 요청 실패 처리와 PostFDS Outbox 저장을 함께 처리한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionLimitExceededEventService {

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    /**
     * 멱등 요청을 FAILED로 변경하고
     * SubscriptionLimitExceeded 이벤트를 Outbox에 저장한다.
     *
     * 호출한 청약 처리에서 BusinessException이 발생하더라도
     * 이 처리 결과가 롤백되지 않도록 별도 트랜잭션을 사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLimitExceeded(
            UUID idempotencyRequestId,
            UUID userId,
            String idempotencyKey,
            UUID assetId,
            Long requestedQuantity,
            Long maxSubscriptionQuantity,
            String correlationId
    ) {
        int failed = idempotencyRequestRepository.fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                        .getStatus()
                        .value()
        );

        if (failed != 1) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
            );
        }

        subscriptionEventPublisher.publishLimitExceeded(
                idempotencyRequestId,
                userId,
                assetId,
                requestedQuantity,
                maxSubscriptionQuantity,
                correlationId
        );
    }
}