package com.moneykk.moneytown.offering.global.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishSchedulerTest {

    @Mock
    private OutboxPublishService outboxPublishService;

    @Mock
    private OutboxKafkaPublisher outboxKafkaPublisher;

    private OutboxPublishScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxPublishScheduler(
                outboxPublishService,
                outboxKafkaPublisher,
                new ObjectMapper()
        );

        ReflectionTestUtils.setField(
                scheduler,
                "batchSize",
                10
        );
    }

    @Test
    @DisplayName("설정한 배치 크기만큼 선점하고 모든 이벤트를 순차 발행한다")
    void publishesAllClaimedEvents() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent first =
                createEvent(firstUserId);

        OutboxPublishService.ClaimedEvent second =
                createEvent(secondUserId);

        when(outboxPublishService.claimPendingEvents(10))
                .thenReturn(List.of(first, second));

        when(outboxKafkaPublisher.publish(any(), anyString()))
                .thenReturn(successfulFuture());

        when(outboxPublishService.markPublished(any()))
                .thenReturn(true);

        scheduler.publishPendingEvents();

        verify(outboxPublishService)
                .claimPendingEvents(10);

        verify(outboxKafkaPublisher)
                .publish(first, firstUserId.toString());

        verify(outboxKafkaPublisher)
                .publish(second, secondUserId.toString());

        verify(outboxPublishService)
                .markPublished(first);

        verify(outboxPublishService)
                .markPublished(second);
    }

    @Test
    @DisplayName("한 이벤트의 Kafka 발행이 실패해도 다음 이벤트를 계속 처리한다")
    void continuesAfterPublishFailure() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent first =
                createEvent(firstUserId);

        OutboxPublishService.ClaimedEvent second =
                createEvent(secondUserId);

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new IllegalStateException("Kafka unavailable")
        );

        when(outboxPublishService.claimPendingEvents(10))
                .thenReturn(List.of(first, second));

        when(outboxKafkaPublisher.publish(
                eq(first),
                eq(firstUserId.toString())
        )).thenReturn(failedFuture);

        when(outboxKafkaPublisher.publish(
                eq(second),
                eq(secondUserId.toString())
        )).thenReturn(successfulFuture());

        when(outboxPublishService.markFailedAttempt(
                eq(first),
                contains("IllegalStateException: Kafka unavailable")
        )).thenReturn(true);

        when(outboxPublishService.markPublished(second))
                .thenReturn(true);

        scheduler.publishPendingEvents();

        verify(outboxPublishService)
                .markFailedAttempt(
                        eq(first),
                        contains("IllegalStateException: Kafka unavailable")
                );

        verify(outboxPublishService, never())
                .markPublished(first);

        verify(outboxKafkaPublisher)
                .publish(second, secondUserId.toString());

        verify(outboxPublishService)
                .markPublished(second);
    }

    @Test
    @DisplayName("발행 대기 중 인터럽트되면 남은 이벤트를 처리하지 않는다")
    void stopsBatchWhenInterrupted() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent first =
                createEvent(firstUserId);

        OutboxPublishService.ClaimedEvent second =
                createEvent(secondUserId);

        CompletableFuture<SendResult<String, String>> pendingFuture =
                new CompletableFuture<>();

        when(outboxPublishService.claimPendingEvents(10))
                .thenReturn(List.of(first, second));

        when(outboxKafkaPublisher.publish(
                first,
                firstUserId.toString()
        )).thenReturn(pendingFuture);

        Thread.currentThread().interrupt();

        try {
            scheduler.publishPendingEvents();

            assertThat(Thread.currentThread().isInterrupted())
                    .isTrue();
        } finally {
            // 다음 테스트에 인터럽트 상태를 남기지 않는다.
            Thread.interrupted();
        }

        verify(outboxKafkaPublisher)
                .publish(first, firstUserId.toString());

        verify(outboxKafkaPublisher, never())
                .publish(second, secondUserId.toString());

        verify(outboxPublishService, never())
                .markPublished(any());

        verify(outboxPublishService, never())
                .markFailedAttempt(any(), anyString());
    }

    @Test
    @DisplayName("청약 한도 초과 이벤트는 userId를 Kafka 메시지 Key로 사용한다")
    void publishesSubscriptionLimitExceededWithUserIdKey() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String envelopeJson = """
            {
              "eventId": "%s",
              "eventType": "SubscriptionLimitExceeded",
              "aggregateId": "%s",
              "userId": "%s",
              "correlationId": "%s",
              "payload": {
                "userId": "%s",
                "assetId": "%s",
                "subscriptionId": null,
                "requestedQuantity": 101,
                "maxSubscriptionQuantity": 100
              }
            }
            """.formatted(
                eventId,
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                userId,
                UUID.randomUUID()
        );

        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "subscription-events",
                        envelopeJson,
                        Instant.parse("2026-09-07T00:00:00Z")
                );

        when(outboxPublishService.claimPendingEvents(10))
                .thenReturn(List.of(event));

        when(outboxKafkaPublisher.publish(
                event,
                userId.toString()
        )).thenReturn(successfulFuture());

        when(outboxPublishService.markPublished(event))
                .thenReturn(true);

        scheduler.publishPendingEvents();

        verify(outboxPublishService)
                .claimPendingEvents(10);

        verify(outboxKafkaPublisher)
                .publish(
                        event,
                        userId.toString()
                );

        verify(outboxPublishService)
                .markPublished(event);

        verify(outboxPublishService, never())
                .markFailedAttempt(
                        any(),
                        anyString()
                );
    }


    private OutboxPublishService.ClaimedEvent createEvent(
            UUID userId
    ) {
        UUID eventId = UUID.randomUUID();

        String envelopeJson = """
                {
                  "eventId": "%s",
                  "eventType": "SubscriptionReserved",
                  "userId": "%s"
                }
                """.formatted(eventId, userId);

        return new OutboxPublishService.ClaimedEvent(
                eventId,
                "subscription-reserved",
                envelopeJson,
                Instant.parse("2026-09-06T00:00:00Z")
        );
    }

    private CompletableFuture<SendResult<String, String>>
    successfulFuture() {
        return CompletableFuture.completedFuture(null);
    }
}
