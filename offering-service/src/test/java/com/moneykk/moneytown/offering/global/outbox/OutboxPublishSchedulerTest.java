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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.times;
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
        scheduler = createScheduler(20);
    }

    @Test
    @DisplayName(
            "설정한 배치 크기만큼 선점하고 모든 이벤트의 발행을 요청한다"
    )
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
    @DisplayName(
            "한 이벤트의 Kafka 발행이 실패해도 다음 이벤트를 계속 처리한다"
    )
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
                        contains(
                                "IllegalStateException: Kafka unavailable"
                        )
                );

        verify(outboxPublishService, never())
                .markPublished(first);

        verify(outboxKafkaPublisher)
                .publish(second, secondUserId.toString());

        verify(outboxPublishService)
                .markPublished(second);
    }

    @Test
    @DisplayName(
            "첫 이벤트의 Kafka 응답을 기다리지 않고 다음 이벤트를 발행한다"
    )
    void publishesNextEventWithoutWaitingForFirstResult() {
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

        when(outboxKafkaPublisher.publish(
                second,
                secondUserId.toString()
        )).thenReturn(successfulFuture());

        when(outboxPublishService.markPublished(first))
                .thenReturn(true);

        when(outboxPublishService.markPublished(second))
                .thenReturn(true);

        scheduler.publishPendingEvents();

        verify(outboxKafkaPublisher)
                .publish(second, secondUserId.toString());

        verify(outboxPublishService, never())
                .markPublished(first);

        verify(outboxPublishService)
                .markPublished(second);

        pendingFuture.complete(null);

        verify(outboxPublishService)
                .markPublished(first);
    }

    @Test
    @DisplayName(
            "청약 한도 초과 이벤트는 userId를 Kafka 메시지 Key로 사용한다"
    )
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

    @Test
    @DisplayName(
            "청약 실패 이벤트는 userId를 Kafka 메시지 Key로 사용한다"
    )
    void publishesSubscriptionFailedWithUserIdKey() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String envelopeJson = """
                {
                  "eventId": "%s",
                  "eventType": "SubscriptionFailed",
                  "aggregateId": "%s",
                  "userId": "%s",
                  "correlationId": "%s",
                  "payload": {
                    "userId": "%s",
                    "assetId": "%s",
                    "subscriptionId": "%s",
                    "failureCode": "INSUFFICIENT_BALANCE"
                  }
                }
                """.formatted(
                eventId,
                subscriptionId,
                userId,
                UUID.randomUUID(),
                userId,
                assetId,
                subscriptionId
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

    @Test
    @DisplayName(
            "Kafka 발행 호출이 즉시 실패해도 다음 이벤트를 처리한다"
    )
    void continuesAfterImmediatePublishFailure() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent first =
                createEvent(firstUserId);

        OutboxPublishService.ClaimedEvent second =
                createEvent(secondUserId);

        when(outboxPublishService.claimPendingEvents(10))
                .thenReturn(List.of(first, second));

        when(outboxKafkaPublisher.publish(
                first,
                firstUserId.toString()
        )).thenThrow(
                new IllegalStateException("Kafka unavailable")
        );

        when(outboxKafkaPublisher.publish(
                second,
                secondUserId.toString()
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
                        contains(
                                "IllegalStateException: Kafka unavailable"
                        )
                );

        verify(outboxKafkaPublisher)
                .publish(second, secondUserId.toString());

        verify(outboxPublishService)
                .markPublished(second);
    }

    @Test
    @DisplayName(
            "최대 동시 발행 수에 도달하면 추가 이벤트를 선점하지 않는다"
    )
    void doesNotClaimWhenMaxInFlightReached() {
        scheduler = createScheduler(1);

        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent first =
                createEvent(firstUserId);

        OutboxPublishService.ClaimedEvent second =
                createEvent(secondUserId);

        CompletableFuture<SendResult<String, String>> pendingFuture =
                new CompletableFuture<>();

        when(outboxPublishService.claimPendingEvents(1))
                .thenReturn(
                        List.of(first),
                        List.of(second)
                );

        when(outboxKafkaPublisher.publish(
                first,
                firstUserId.toString()
        )).thenReturn(pendingFuture);

        when(outboxKafkaPublisher.publish(
                second,
                secondUserId.toString()
        )).thenReturn(successfulFuture());

        when(outboxPublishService.markPublished(first))
                .thenReturn(true);

        when(outboxPublishService.markPublished(second))
                .thenReturn(true);

        // 첫 이벤트가 Kafka 응답을 기다리면서 유일한 슬롯을 점유한다.
        scheduler.publishPendingEvents();

        // 사용할 수 있는 슬롯이 없으므로 DB 이벤트를 선점하지 않는다.
        scheduler.publishPendingEvents();

        verify(outboxPublishService, times(1))
                .claimPendingEvents(1);

        verify(outboxKafkaPublisher, never())
                .publish(
                        second,
                        secondUserId.toString()
                );

        // Kafka 응답 완료 후 슬롯이 반환된다.
        pendingFuture.complete(null);

        // 반환된 슬롯을 이용해 다음 이벤트를 선점한다.
        scheduler.publishPendingEvents();

        verify(outboxPublishService, times(2))
                .claimPendingEvents(1);

        verify(outboxKafkaPublisher)
                .publish(
                        second,
                        secondUserId.toString()
                );

        verify(outboxPublishService)
                .markPublished(second);
    }

    @Test
    @DisplayName(
            "Kafka 발행이 즉시 실패하면 슬롯을 반환하여 다음 이벤트를 처리한다"
    )
    void releasesSlotAfterImmediatePublishFailure() {
        scheduler = createScheduler(1);

        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent first =
                createEvent(firstUserId);

        OutboxPublishService.ClaimedEvent second =
                createEvent(secondUserId);

        when(outboxPublishService.claimPendingEvents(1))
                .thenReturn(
                        List.of(first),
                        List.of(second)
                );

        when(outboxKafkaPublisher.publish(
                first,
                firstUserId.toString()
        )).thenThrow(
                new IllegalStateException("Kafka unavailable")
        );

        when(outboxKafkaPublisher.publish(
                second,
                secondUserId.toString()
        )).thenReturn(successfulFuture());

        when(outboxPublishService.markFailedAttempt(
                eq(first),
                contains("IllegalStateException: Kafka unavailable")
        )).thenReturn(true);

        when(outboxPublishService.markPublished(second))
                .thenReturn(true);

        // 첫 발행이 즉시 실패하면서 슬롯이 반환된다.
        scheduler.publishPendingEvents();

        // 반환된 슬롯으로 다음 이벤트를 처리한다.
        scheduler.publishPendingEvents();

        verify(outboxPublishService, times(2))
                .claimPendingEvents(1);

        verify(outboxPublishService)
                .markFailedAttempt(
                        eq(first),
                        contains(
                                "IllegalStateException: Kafka unavailable"
                        )
                );

        verify(outboxKafkaPublisher)
                .publish(
                        second,
                        secondUserId.toString()
                );

        verify(outboxPublishService)
                .markPublished(second);
    }

    private OutboxPublishScheduler createScheduler(
            int maxInFlight
    ) {
        // 기존 테스트 동작을 유지하기 위해 즉시 실행 executor 사용
        return createScheduler(
                maxInFlight,
                Runnable::run
        );
    }

    private OutboxPublishScheduler createScheduler(
            int maxInFlight,
            Executor publishExecutor
    ) {
        OutboxPublishMonitor monitor =
                new OutboxPublishMonitor(
                        outboxPublishService,
                        new SimpleMeterRegistry(),
                        maxInFlight
                );

        OutboxPublishScheduler createdScheduler =
                new OutboxPublishScheduler(
                        outboxPublishService,
                        outboxKafkaPublisher,
                        new ObjectMapper(),

                        publishExecutor,

                        // callback executor
                        Runnable::run,
                        monitor
                );

        ReflectionTestUtils.setField(
                createdScheduler,
                "batchSize",
                10
        );

        return createdScheduler;
    }

    @Test
    @DisplayName(
            "발행 executor가 작업을 거절하면 실패를 기록하고 슬롯을 반환한다"
    )
    void recordsFailureAndReleasesSlotWhenExecutorRejectsTask() {
        // given
        scheduler = createScheduler(
                1,
                command -> {
                    throw new RejectedExecutionException(
                            "publish executor saturated"
                    );
                }
        );

        UUID userId = UUID.randomUUID();

        OutboxPublishService.ClaimedEvent event =
                createEvent(userId);

        when(outboxPublishService.claimPendingEvents(1))
                .thenReturn(
                        List.of(event),
                        List.of()
                );

        when(outboxPublishService.markFailedAttempt(
                eq(event),
                contains(
                        "RejectedExecutionException: "
                                + "publish executor saturated"
                )
        )).thenReturn(true);

        // when
        scheduler.publishPendingEvents();

        /*
         * 첫 요청에서 슬롯이 정상 반환됐다면
         * 두 번째 실행에서도 다시 이벤트 선점을 시도할 수 있다.
         */
        scheduler.publishPendingEvents();

        // then
        verify(outboxPublishService, times(2))
                .claimPendingEvents(1);

        verify(outboxPublishService)
                .markFailedAttempt(
                        eq(event),
                        contains(
                                "RejectedExecutionException: "
                                        + "publish executor saturated"
                        )
                );

        verify(outboxKafkaPublisher, never())
                .publish(
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
