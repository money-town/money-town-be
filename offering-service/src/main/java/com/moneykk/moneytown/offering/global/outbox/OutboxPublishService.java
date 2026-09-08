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

    @Value("${outbox.publish.retry-delay-seconds:30}")
    private long retryDelaySeconds;

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
     * @return 현재 발행 시도에 결과가 반영되었으면 true
     */
    @Transactional
    public boolean markFailedAttempt(
            ClaimedEvent event,
            String lastError
    ) {
        validateClaimedEvent(event);

        if (maxFailedAttempts <= 0 || retryDelaySeconds <= 0) {
            throw new IllegalStateException(
                    "발행 실패 한도와 재시도 간격은 1 이상이어야 합니다."
            );
        }

        String errorMessage =
                lastError == null || lastError.isBlank()
                        ? "Kafka 발행 중 원인을 확인할 수 없는 오류가 발생했습니다."
                        : lastError;

        Instant nextRetryAt = outboxEventRepository
                .getCurrentDatabaseTime()
                .plusSeconds(retryDelaySeconds);

        int updatedRows = outboxEventRepository.markFailedAttempt(
                event.eventId(),
                event.processingStartedAt(),
                errorMessage,
                maxFailedAttempts,
                nextRetryAt
        );

        return updatedRows == 1;
    }

    private void validateClaimedEvent(ClaimedEvent event) {
        Objects.requireNonNull(event, "발행 이벤트는 필수입니다.");
        Objects.requireNonNull(event.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(
                event.processingStartedAt(),
                "발행 처리 시작 시각은 필수입니다."
        );
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

        if (processingTimeoutSeconds <= 0
                || maxFailedAttempts <= 0
                || retryDelaySeconds <= 0) {
            throw new IllegalStateException(
                    "처리 제한 시간, 실패 한도, 재시도 간격은 1 이상이어야 합니다."
            );
        }

        Instant now = outboxEventRepository.getCurrentDatabaseTime();

        Instant expiredBefore =
                now.minusSeconds(processingTimeoutSeconds);

        Instant nextRetryAt =
                now.plusSeconds(retryDelaySeconds);

        return outboxEventRepository.recoverExpiredProcessing(
                expiredBefore,
                maxFailedAttempts,
                nextRetryAt,
                batchSize
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