package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionRequestProcessingService {

    private final SubscriptionRequestStateService stateService;
    private final SubscriptionCommandService commandService;
    private final SubscriptionRequestMetrics metrics;

    public void process(UUID requestId) {
        var work = stateService.start(requestId).orElse(null);

        // Kafka 재전달 시 이미 종료된 접수는 멱등하게 ACK한다.
        if (work == null) {
            return;
        }

        Instant startedAt = Instant.now();
        try {
            commandService.processAcceptedRequest(
                    work.requestId(),
                    work.offeringId(),
                    work.userId(),
                    work.idempotencyKey(),
                    work.quantity(),
                    work.correlationId()
            );
            metrics.recordCompleted();
        } catch (BusinessException e) {
            if (e.getErrorCode().getStatus().is5xxServerError()) {
                throw new SubscriptionRequestRetryableException(
                        e.getErrorCode().getCode(),
                        e
                );
            }

            stateService.reject(requestId, e.getErrorCode().getCode());
            metrics.recordRejected();
        } catch (RuntimeException e) {
            throw new SubscriptionRequestRetryableException(
                    "비동기 청약 처리 중 일시 오류가 발생했습니다.",
                    e
            );
        } finally {
            metrics.recordProcessing(Duration.between(startedAt, Instant.now()));
        }
    }
}
