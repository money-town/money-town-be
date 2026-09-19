package com.moneykk.moneytown.offering.global.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxPublishService outboxPublishService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                outboxPublishService,
                "maxFailedAttempts",
                3
        );
        ReflectionTestUtils.setField(
                outboxPublishService,
                "initialRetryDelaySeconds",
                10L
        );
        ReflectionTestUtils.setField(
                outboxPublishService,
                "retryMultiplier",
                2.0
        );
        ReflectionTestUtils.setField(
                outboxPublishService,
                "maxRetryDelaySeconds",
                60L
        );
        ReflectionTestUtils.setField(
                outboxPublishService,
                "processingTimeoutSeconds",
                300L
        );
    }

    @Test
    @DisplayName("Outbox 발행 실패 시 지수 백오프 설정으로 재시도를 예약한다")
    void marksFailedAttemptWithRetryPolicy() {
        // given
        UUID eventId = UUID.randomUUID();
        Instant processingStartedAt =
                Instant.parse("2026-09-17T01:00:00Z");

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "subscription-confirmed",
                        "{\"eventType\":\"SubscriptionConfirmed\"}",
                        processingStartedAt
                );

        when(outboxEventRepository.markFailedAttempt(
                eventId,
                processingStartedAt,
                "Kafka unavailable",
                3,
                10L,
                2.0,
                60L
        )).thenReturn(1);

        // when
        boolean result =
                outboxPublishService.markFailedAttempt(
                        event,
                        "Kafka unavailable"
                );

        // then
        assertThat(result).isTrue();

        verify(outboxEventRepository).markFailedAttempt(
                eventId,
                processingStartedAt,
                "Kafka unavailable",
                3,
                10L,
                2.0,
                60L
        );
    }

    @Test
    @DisplayName("처리 기한이 지난 PROCESSING 이벤트를 지수 백오프 설정으로 복구한다")
    void recoversExpiredProcessingWithRetryPolicy() {
        // given
        Instant now =
                Instant.parse("2026-09-17T01:10:00Z");
        Instant expiredBefore =
                Instant.parse("2026-09-17T01:05:00Z");

        when(outboxEventRepository.getCurrentDatabaseTime())
                .thenReturn(now);

        when(outboxEventRepository.recoverExpiredProcessing(
                expiredBefore,
                3,
                10L,
                2.0,
                60L,
                100
        )).thenReturn(2);

        // when
        int result =
                outboxPublishService.recoverExpiredProcessing(
                        100
                );

        // then
        assertThat(result).isEqualTo(2);

        verify(outboxEventRepository)
                .recoverExpiredProcessing(
                        expiredBefore,
                        3,
                        10L,
                        2.0,
                        60L,
                        100
                );
    }

    @Test
    @DisplayName("DB에 남아 있는 FAILED Outbox 이벤트 수를 반환한다")
    void countsFailedEvents() {
        // given
        when(outboxEventRepository.countByEventStatus(
                OutboxEventStatus.FAILED
        )).thenReturn(5L);

        // when
        long result =
                outboxPublishService.countFailedEvents();

        // then
        assertThat(result).isEqualTo(5L);

        verify(outboxEventRepository)
                .countByEventStatus(
                        OutboxEventStatus.FAILED
                );
    }

    @Test
    @DisplayName("가장 오래된 PENDING Outbox 이벤트의 대기시간을 반환한다")
    void getsOldestPendingAge() {
        // given
        when(outboxEventRepository.findOldestPendingAgeSeconds())
                .thenReturn(12L);

        // when
        long result =
                outboxPublishService.getOldestPendingAgeSeconds();

        // then
        assertThat(result).isEqualTo(12L);

        verify(outboxEventRepository)
                .findOldestPendingAgeSeconds();
    }

    @Test
    @DisplayName("FAILED 이벤트를 PENDING으로 전환하면 재처리 요청 성공을 반환한다")
    void requeuesFailedEvent() {
        // given
        UUID eventId = UUID.randomUUID();

        when(outboxEventRepository.requeueFailedEvent(eventId))
                .thenReturn(1);

        // when
        boolean result =
                outboxPublishService.requeueFailedEvent(
                        eventId
                );

        // then
        assertThat(result).isTrue();

        verify(outboxEventRepository)
                .requeueFailedEvent(eventId);
    }

    @Test
    @DisplayName("FAILED 상태가 아닌 이벤트는 재처리 요청 실패를 반환한다")
    void returnsFalseWhenEventCannotBeRequeued() {
        // given
        UUID eventId = UUID.randomUUID();

        /*
         * 이벤트가 없거나 현재 상태가
         * PENDING, PROCESSING, PUBLISHED인 경우를 표현한다.
         */
        when(outboxEventRepository.requeueFailedEvent(eventId))
                .thenReturn(0);

        // when
        boolean result =
                outboxPublishService.requeueFailedEvent(
                        eventId
                );

        // then
        assertThat(result).isFalse();

        verify(outboxEventRepository)
                .requeueFailedEvent(eventId);
    }

    @Test
    @DisplayName("재시도할 수 없는 Outbox 오류를 즉시 영구 실패로 기록한다")
    void marksPermanentFailure() {
        // given
        UUID eventId = UUID.randomUUID();
        Instant processingStartedAt =
                Instant.parse("2026-09-17T01:00:00Z");

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "subscription-events",
                        "invalid-json",
                        processingStartedAt
                );

        when(outboxEventRepository.markPermanentFailure(
                eventId,
                processingStartedAt,
                "JsonParseException: invalid JSON"
        )).thenReturn(1);

        // when
        boolean result =
                outboxPublishService.markPermanentFailure(
                        event,
                        "JsonParseException: invalid JSON"
                );

        // then
        assertThat(result).isTrue();

        verify(outboxEventRepository)
                .markPermanentFailure(
                        eventId,
                        processingStartedAt,
                        "JsonParseException: invalid JSON"
                );
    }

    @Test
    @DisplayName("발행 대기 이벤트를 선점하면 PROCESSING으로 전환하고 발행 대상 목록을 반환한다")
    void claimsPendingEvents() {
        // given
        Instant startedAt = Instant.parse("2026-09-17T01:00:00Z");
        UUID eventId = UUID.randomUUID();

        OutboxEvent event = OutboxEvent.create(
                eventId,
                "Subscription",
                UUID.randomUUID(),
                "SubscriptionConfirmed",
                "subscription-confirmed",
                "{\"eventType\":\"SubscriptionConfirmed\"}"
        );

        when(outboxEventRepository.getCurrentDatabaseTime())
                .thenReturn(startedAt);
        when(outboxEventRepository.findPublishableEventsForUpdate(100))
                .thenReturn(List.of(event));

        // when
        List<OutboxPublishService.ClaimedEvent> claimed =
                outboxPublishService.claimPendingEvents(100);

        // then
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).eventId()).isEqualTo(eventId);
        assertThat(claimed.get(0).processingStartedAt())
                .isEqualTo(startedAt);
        assertThat(event.getEventStatus())
                .isEqualTo(OutboxEventStatus.PROCESSING);
    }

    @Test
    @DisplayName("배치 크기가 1보다 작으면 발행 대상을 선점하지 않는다")
    void rejectsInvalidBatchSizeOnClaim() {
        // when & then
        assertThatThrownBy(() ->
                outboxPublishService.claimPendingEvents(0)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("배치 크기가 1보다 작으면 만료된 처리 건을 복구하지 않는다")
    void rejectsInvalidBatchSizeOnRecover() {
        // when & then
        assertThatThrownBy(() ->
                outboxPublishService.recoverExpiredProcessing(0)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("처리 제한 시간이 0 이하면 만료된 처리 건을 복구하지 않는다")
    void rejectsInvalidProcessingTimeoutOnRecover() {
        // given
        ReflectionTestUtils.setField(
                outboxPublishService,
                "processingTimeoutSeconds",
                0L
        );

        // when & then
        assertThatThrownBy(() ->
                outboxPublishService.recoverExpiredProcessing(100)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("발행 성공을 정상적으로 기록한다")
    void marksPublishedSuccessfully() {
        // given
        UUID eventId = UUID.randomUUID();
        Instant processingStartedAt =
                Instant.parse("2026-09-17T01:00:00Z");

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "subscription-confirmed",
                        "{\"eventType\":\"SubscriptionConfirmed\"}",
                        processingStartedAt
                );

        when(outboxEventRepository.markPublished(
                eventId, processingStartedAt
        )).thenReturn(1);

        // when
        boolean result = outboxPublishService.markPublished(event);

        // then
        assertThat(result).isTrue();

        verify(outboxEventRepository)
                .markPublished(eventId, processingStartedAt);
    }

    @Test
    @DisplayName("이미 다른 시도에서 처리된 이벤트는 발행 성공 기록이 반영되지 않는다")
    void markPublishedReturnsFalseWhenNotUpdated() {
        // given
        UUID eventId = UUID.randomUUID();
        Instant processingStartedAt =
                Instant.parse("2026-09-17T01:00:00Z");

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "subscription-confirmed",
                        "{\"eventType\":\"SubscriptionConfirmed\"}",
                        processingStartedAt
                );

        when(outboxEventRepository.markPublished(
                eventId, processingStartedAt
        )).thenReturn(0);

        // when
        boolean result = outboxPublishService.markPublished(event);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("발행 이벤트가 없으면 발행 성공 기록에 실패한다")
    void marksPublishedRejectsNullEvent() {
        // when & then
        assertThatThrownBy(() ->
                outboxPublishService.markPublished(null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("발행 실패 원인이 없으면 기본 오류 메시지로 대체하여 기록한다")
    void marksFailedAttemptWithDefaultErrorMessageWhenBlank() {
        // given
        UUID eventId = UUID.randomUUID();
        Instant processingStartedAt =
                Instant.parse("2026-09-17T01:00:00Z");

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "subscription-confirmed",
                        "{\"eventType\":\"SubscriptionConfirmed\"}",
                        processingStartedAt
                );

        when(outboxEventRepository.markFailedAttempt(
                eventId,
                processingStartedAt,
                "Kafka 발행 중 원인을 확인할 수 없는 오류가 발생했습니다.",
                3, 10L, 2.0, 60L
        )).thenReturn(1);

        // when
        boolean result = outboxPublishService.markFailedAttempt(
                event, "  "
        );

        // then
        assertThat(result).isTrue();

        verify(outboxEventRepository).markFailedAttempt(
                eventId,
                processingStartedAt,
                "Kafka 발행 중 원인을 확인할 수 없는 오류가 발생했습니다.",
                3, 10L, 2.0, 60L
        );
    }

    @Test
    @DisplayName("발행 실패 한도가 0 이하이면 재시도를 예약할 수 없다")
    void rejectsInvalidMaxFailedAttempts() {
        // given
        ReflectionTestUtils.setField(
                outboxPublishService, "maxFailedAttempts", 0
        );

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        UUID.randomUUID(),
                        "subscription-confirmed",
                        "{}",
                        Instant.now()
                );

        // when & then
        assertThatThrownBy(() ->
                outboxPublishService.markFailedAttempt(event, "error")
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PENDING Outbox 이벤트 수를 조회한다")
    void countsPendingEvents() {
        // given
        when(outboxEventRepository.countByEventStatus(
                OutboxEventStatus.PENDING
        )).thenReturn(7L);

        // when
        long result = outboxPublishService.countPendingEvents();

        // then
        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("PROCESSING Outbox 이벤트 수를 조회한다")
    void countsProcessingEvents() {
        // given
        when(outboxEventRepository.countByEventStatus(
                OutboxEventStatus.PROCESSING
        )).thenReturn(2L);

        // when
        long result = outboxPublishService.countProcessingEvents();

        // then
        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("eventId가 없으면 재처리 요청을 거부한다")
    void requeueRejectsNullEventId() {
        // when & then
        assertThatThrownBy(() ->
                outboxPublishService.requeueFailedEvent(null)
        ).isInstanceOf(NullPointerException.class);
    }
}