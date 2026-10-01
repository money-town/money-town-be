package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionRequestStateService {

    private final SubscriptionRequestRepository requestRepository;
    private final OfferingRepository offeringRepository;
    private final SubscriptionEventPublisher eventPublisher;

    @Transactional
    public Optional<SubscriptionRequestWork> start(UUID requestId) {
        SubscriptionRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "청약 접수를 찾을 수 없습니다. requestId=" + requestId
                ));

        if (request.isTerminal()) {
            return Optional.empty();
        }

        request.startProcessing(Instant.now());
        return Optional.of(SubscriptionRequestWork.from(request));
    }

    @Transactional
    public void reject(UUID requestId, String failureCode) {
        SubscriptionRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "청약 접수를 찾을 수 없습니다. requestId=" + requestId
                ));

        if (request.isTerminal()) {
            return;
        }

        request.reject(failureCode, Instant.now());

        if (SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                .getCode().equals(failureCode)) {
            Offering offering = offeringRepository
                    .findByOfferingIdAndIsDeletedFalse(request.getOfferingId())
                    .orElseThrow(() -> new com.moneykk.moneytown.common.exception.BusinessException(
                            OfferingErrorCode.OFFERING_NOT_FOUND
                    ));

            eventPublisher.publishLimitExceeded(
                    request.getSubscriptionRequestId(),
                    request.getUserId(),
                    offering.getAssetId(),
                    request.getQuantity(),
                    offering.getMaxSubscriptionQuantity(),
                    request.getCorrelationId()
            );
        }
    }

    @Transactional
    public void fail(UUID requestId, String failureCode) {
        SubscriptionRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "청약 접수를 찾을 수 없습니다. requestId=" + requestId
                ));

        if (!request.isTerminal()) {
            request.fail(failureCode, Instant.now());
        }
    }

    public record SubscriptionRequestWork(
            UUID requestId,
            UUID offeringId,
            UUID userId,
            Long quantity,
            String idempotencyKey,
            String requestHash,
            String correlationId
    ) {
        static SubscriptionRequestWork from(SubscriptionRequest request) {
            return new SubscriptionRequestWork(
                    request.getSubscriptionRequestId(),
                    request.getOfferingId(),
                    request.getUserId(),
                    request.getQuantity(),
                    request.getIdempotencyKey(),
                    request.getRequestHash(),
                    request.getCorrelationId()
            );
        }
    }
}
