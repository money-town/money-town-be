package com.moneykk.moneytown.offering.global.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxPublishService {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${outbox.publish.max-failed-attempts:3}")
    private int maxFailedAttempts;

    @Value("${outbox.publish.initial-retry-delay-seconds:10}")
    private long initialRetryDelaySeconds;

    @Value("${outbox.publish.retry-multiplier:2.0}")
    private double retryMultiplier;

    @Value("${outbox.publish.max-retry-delay-seconds:60}")
    private long maxRetryDelaySeconds;

    @Value("${outbox.publish.processing-timeout-seconds:300}")
    private long processingTimeoutSeconds;

    @Transactional
    public List<ClaimedEvent> claimPendingEvents(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize는 1 이상이어야 합니다."
            );
        }

        Instant startedAt =
                outboxEventRepository.getCurrentDatabaseTime();

        List<OutboxEvent> events =
                outboxEventRepository.findPublishableEventsForUpdate(
                        batchSize
                );

        for (OutboxEvent event : events) {
            event.startProcessing(startedAt);
        }

        return events.stream()
                .map(event -> new ClaimedEvent(
                        event.getEventId(),
                        event.getTopic(),
                        event.getPayload(),
                        event.getProcessingStartedAt()
                ))
                .toList();
    }

    /**
     * Kafka 발행 성공 결과를 기록한다.
     *
     * @return 현재 발행 시도에 결과가 반영되었으면 true
     */
    @Transactional
    public boolean markPublished(ClaimedEvent event) {
        validateClaimedEvent(event);

        int updatedRows = outboxEventRepository.markPublished(
                event.eventId(),
                event.processingStartedAt()
        );

        return updatedRows == 1;
    }

    /**
     * Kafka 발행 실패를 기록하고 재시도를 예약한다.
     *
     *  실패 누적 횟수가 설정된 한도에 도달하면
     *  Outbox 이벤트를 FAILED 상태로 전환한다.
     *
     * @return 현재 발행 시도에 결과가 반영되었으면 true
     */
    @Transactional
    public boolean markFailedAttempt(
            ClaimedEvent event,
            String lastError
    ) {
        validateClaimedEvent(event);
        validateRetryPolicy();

        String errorMessage =
                normalizeLastError(lastError);

        int updatedRows =
                outboxEventRepository.markFailedAttempt(
                        event.eventId(),
                        event.processingStartedAt(),
                        errorMessage,
                        maxFailedAttempts,
                        initialRetryDelaySeconds,
                        retryMultiplier,
                        maxRetryDelaySeconds
                );

        return updatedRows == 1;
    }

    /**
     * 다시 처리해도 성공할 수 없는 Outbox 메시지를
     * 재시도 없이 즉시 FAILED 상태로 전환한다.
     *
     * @return 현재 발행 시도에 실패 결과가 반영됐으면 true
     */
    @Transactional
    public boolean markPermanentFailure(
            ClaimedEvent event,
            String lastError
    ) {
        validateClaimedEvent(event);

        String errorMessage =
                normalizeLastError(lastError);

        int updatedRows =
                outboxEventRepository.markPermanentFailure(
                        event.eventId(),
                        event.processingStartedAt(),
                        errorMessage
                );

        return updatedRows == 1;
    }

    /**
     * 장시간 PROCESSING 상태인 이벤트를 재시도 또는 FAILED로 전환한다.
     *
     * @return 복구 처리한 이벤트 수
     */
    @Transactional
    public int recoverExpiredProcessing(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize는 1 이상이어야 합니다."
            );
        }

        validateRetryPolicy();

        if (processingTimeoutSeconds <= 0) {
            throw new IllegalStateException(
                    "처리 제한 시간은 1 이상이어야 합니다."
            );
        }

        Instant now =
                outboxEventRepository.getCurrentDatabaseTime();

        Instant expiredBefore =
                now.minusSeconds(processingTimeoutSeconds);

        return outboxEventRepository.recoverExpiredProcessing(
                expiredBefore,
                maxFailedAttempts,
                initialRetryDelaySeconds,
                retryMultiplier,
                maxRetryDelaySeconds,
                batchSize
        );
    }

    /*
     * 원인이 해결된 FAILED 이벤트를 다시 발행할 수 있도록
     * PENDING 상태로 전환한다.
     *
     * 같은 이벤트에 재처리를 여러 번 요청하거나,
     * 이미 PENDING/PROCESSING/PUBLISHED 상태라면 false를 반환한다.
     *
     * @return FAILED에서 PENDING으로 변경됐으면 true
     */
    @Transactional
    public boolean requeueFailedEvent(
            UUID eventId
    ) {
        Objects.requireNonNull(
                eventId,
                "eventId는 필수입니다."
        );

        int updatedRows =
                outboxEventRepository.requeueFailedEvent(
                        eventId
                );

        return updatedRows == 1;
    }

    /**
     * Kafka 발행을 기다리는 PENDING Outbox 이벤트 수를 조회한다.
     */
    @Transactional(readOnly = true)
    public long countPendingEvents() {
        return outboxEventRepository.countByEventStatus(
                OutboxEventStatus.PENDING
        );
    }

    /**
     * 가장 오래 발행을 기다린 PENDING 이벤트의 대기시간을 조회한다.
     */
    @Transactional(readOnly = true)
    public long getOldestPendingAgeSeconds() {
        return Math.max(
                0L,
                outboxEventRepository.findOldestPendingAgeSeconds()
        );
    }

    /**
     * 현재 DB에서 발행 처리 중인 Outbox 이벤트 수를 조회한다.
     *
     * OutboxPublishMonitor가 PROCESSING 건수 Gauge를 갱신할 때 사용한다.
     */
    @Transactional(readOnly = true)
    public long countProcessingEvents() {
        return outboxEventRepository.countByEventStatus(
                OutboxEventStatus.PROCESSING
        );
    }

    /**
     * 현재 DB에서 영구 실패 상태로 남아 있는
     * FAILED Outbox 이벤트 수를 조회한다.
     *
     * OutboxPublishMonitor가 FAILED Gauge를 갱신할 때 사용한다.
     */
    @Transactional(readOnly = true)
    public long countFailedEvents() {
        return outboxEventRepository.countByEventStatus(
                OutboxEventStatus.FAILED
        );
    }

    private String normalizeLastError(String lastError) {
        if (lastError == null || lastError.isBlank()) {
            return "Kafka 발행 중 원인을 확인할 수 없는 오류가 발생했습니다.";
        }

        return lastError;
    }

    private void validateRetryPolicy() {
        if (maxFailedAttempts <= 0) {
            throw new IllegalStateException(
                    "발행 실패 한도는 1 이상이어야 합니다."
            );
        }

        if (initialRetryDelaySeconds <= 0) {
            throw new IllegalStateException(
                    "최초 재시도 간격은 1초 이상이어야 합니다."
            );
        }

        if (retryMultiplier < 1.0) {
            throw new IllegalStateException(
                    "재시도 간격 배수는 1.0 이상이어야 합니다."
            );
        }

        if (maxRetryDelaySeconds < initialRetryDelaySeconds) {
            throw new IllegalStateException(
                    "최대 재시도 간격은 최초 재시도 간격 이상이어야 합니다."
            );
        }
    }

    private void validateClaimedEvent(ClaimedEvent event) {
        Objects.requireNonNull(event, "발행 이벤트는 필수입니다.");
        Objects.requireNonNull(event.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(
                event.processingStartedAt(),
                "발행 처리 시작 시각은 필수입니다."
        );
    }

    public record ClaimedEvent(
            UUID eventId,
            String topic,
            String envelopeJson,
            Instant processingStartedAt
    ) {
    }
}
