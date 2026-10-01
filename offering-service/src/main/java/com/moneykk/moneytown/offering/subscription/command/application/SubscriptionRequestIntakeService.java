package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRequestAcceptedResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRequestAcceptedResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionRequestIntakeService {

    private final SubscriptionRequestRepository requestRepository;
    private final SubscriptionRequestHasher requestHasher;
    private final SubscriptionEventPublisher eventPublisher;
    private final SubscriptionRequestMetrics metrics;

    /**
     * 요청 상태와 Outbox 이벤트만 짧은 로컬 트랜잭션으로 저장한다.
     * User/FDS 호출과 수량 선점은 Kafka Consumer가 수행한다.
     */
    @Transactional
    public SubscriptionRequestAcceptedResult accept(
            UUID offeringId,
            UUID userId,
            String idempotencyKey,
            SubscriptionCreateRequest command,
            String correlationId
    ) {
        validate(offeringId, userId, idempotencyKey, command, correlationId);

        String requestHash = requestHasher.hash(offeringId, command.quantity());
        UUID requestId = UUID.randomUUID();

        int inserted = requestRepository.insertIfAbsent(
                requestId,
                offeringId,
                userId,
                command.quantity(),
                idempotencyKey,
                requestHash,
                correlationId
        );

        if (inserted == 0) {
            SubscriptionRequest existing = requestRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new BusinessException(
                            SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
                    ));

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(
                        SubscriptionErrorCode.IDEMPOTENCY_KEY_CONFLICT
                );
            }

            metrics.recordReplayed();
            return new SubscriptionRequestAcceptedResult(
                    SubscriptionRequestAcceptedResponse.from(existing),
                    true
            );
        }

        // INSERT 직후 재조회하지 않고 같은 값의 스냅샷으로 Outbox를 생성한다.
        // HTTP 접수 경로의 DB round trip은 request INSERT와 outbox INSERT로 제한한다.
        SubscriptionRequest created = SubscriptionRequest.create(
                requestId,
                offeringId,
                userId,
                command.quantity(),
                idempotencyKey,
                requestHash,
                correlationId
        );

        eventPublisher.publishRequested(created);
        metrics.recordAccepted();

        return new SubscriptionRequestAcceptedResult(
                SubscriptionRequestAcceptedResponse.from(created),
                false
        );
    }

    private void validate(
            UUID offeringId,
            UUID userId,
            String idempotencyKey,
            SubscriptionCreateRequest command,
            String correlationId
    ) {
        if (offeringId == null || userId == null || command == null
                || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 100) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }
        if (command.quantity() == null || command.quantity() <= 0) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY
            );
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY
            );
        }
    }
}
