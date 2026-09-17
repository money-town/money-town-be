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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}