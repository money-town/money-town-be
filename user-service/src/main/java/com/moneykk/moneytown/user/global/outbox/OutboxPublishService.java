package com.moneykk.moneytown.user.global.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new IllegalArgumentException("batchSize는 1 이상이어야 합니다.");
        }

        Instant startedAt = outboxEventRepository.getCurrentDatabaseTime();
        List<OutboxEvent> events =
                outboxEventRepository.findPublishableEventsForUpdate(batchSize);

        events.forEach(event -> event.startProcessing(startedAt));

        return events.stream()
                .map(event -> new ClaimedEvent(
                        event.getEventId(),
                        event.getTopic(),
                        event.getPayload(),
                        event.getProcessingStartedAt()
                ))
                .toList();
    }

    @Transactional
    public boolean markPublished(ClaimedEvent event) {
        validateClaimedEvent(event);

        return outboxEventRepository.markPublished(
                event.eventId(),
                event.processingStartedAt()
        ) == 1;
    }

    @Transactional
    public boolean markFailedAttempt(
            ClaimedEvent event,
            String lastError
    ) {
        validateClaimedEvent(event);
        validateRetryConfiguration();

        String errorMessage = lastError == null || lastError.isBlank()
                ? "Kafka 발행 중 알 수 없는 오류 발생"
                : lastError;

        Instant nextRetryAt = outboxEventRepository
                .getCurrentDatabaseTime()
                .plusSeconds(retryDelaySeconds);

        return outboxEventRepository.markFailedAttempt(
                event.eventId(),
                event.processingStartedAt(),
                errorMessage,
                maxFailedAttempts,
                nextRetryAt
        ) == 1;
    }

    @Transactional
    public int recoverExpiredProcessing(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 1 이상이어야 합니다.");
        }
        validateRetryConfiguration();

        if (processingTimeoutSeconds <= 0) {
            throw new IllegalStateException("처리 제한 시간은 1초 이상이어야 합니다.");
        }

        Instant now = outboxEventRepository.getCurrentDatabaseTime();

        return outboxEventRepository.recoverExpiredProcessing(
                now.minusSeconds(processingTimeoutSeconds),
                maxFailedAttempts,
                now.plusSeconds(retryDelaySeconds),
                batchSize
        );
    }

    private void validateClaimedEvent(ClaimedEvent event) {
        Objects.requireNonNull(event, "발행 이벤트는 필수입니다.");
        Objects.requireNonNull(event.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(
                event.processingStartedAt(),
                "발행 처리 시작 시각은 필수입니다."
        );
    }

    private void validateRetryConfiguration() {
        if (maxFailedAttempts <= 0 || retryDelaySeconds <= 0) {
            throw new IllegalStateException(
                    "발행 실패 한도와 재시도 간격은 1 이상이어야 합니다."
            );
        }
    }

    public record ClaimedEvent(
            UUID eventId,
            String topic,
            String envelopeJson,
            Instant processingStartedAt
    ) {
    }
}
