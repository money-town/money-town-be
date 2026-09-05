package com.moneykk.moneytown.offering.global.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "p_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    // EventEnvelope의 eventId를 그대로 저장한다.
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(
            name = "aggregate_type",
            nullable = false,
            updatable = false,
            length = 50
    )
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(
            name = "event_type",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String eventType;

    @Column(name = "topic", nullable = false, updatable = false, length = 100)
    private String topic;

    // 직렬화된 전체 EventEnvelope JSON을 저장한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private OutboxEventStatus eventStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    private OutboxEvent(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String topic,
            String payload
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId는 필수입니다.");
        this.aggregateType = requireText(aggregateType, "aggregateType", 50);
        this.aggregateId = Objects.requireNonNull(
                aggregateId,
                "aggregateId는 필수입니다."
        );
        this.eventType = requireText(eventType, "eventType", 100);
        this.topic = requireText(topic, "topic", 100);

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload는 필수입니다.");
        }
        this.payload = payload;

        this.eventStatus = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public static OutboxEvent create(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String topic,
            String payload
    ) {
        return new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                payload
        );
    }

    public void startProcessing(Instant startedAt) {
        Objects.requireNonNull(startedAt, "처리 시작 시각은 필수입니다.");

        if (eventStatus != OutboxEventStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 이벤트만 발행 처리를 시작할 수 있습니다."
            );
        }

        if (nextRetryAt != null && nextRetryAt.isAfter(startedAt)) {
            throw new IllegalStateException(
                    "아직 재시도 예정 시각에 도달하지 않았습니다."
            );
        }

        this.eventStatus = OutboxEventStatus.PROCESSING;
        this.processingStartedAt = startedAt;
        this.nextRetryAt = null;
    }

    private static String requireText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없고 "
                            + maxLength + "자를 초과할 수 없습니다."
            );
        }
        return value;
    }
}