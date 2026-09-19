package com.moneykk.moneytown.asset.global.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishSchedulerTest {

    @Mock
    private OutboxPublishService outboxPublishService;

    @Mock
    private OutboxKafkaPublisher outboxKafkaPublisher;

    @ParameterizedTest
    @ValueSource(strings = {"AssetTerminationRequested", "RevenueReady"})
    void usesAssetIdAsKafkaKey(String eventType) {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OutboxPublishService.ClaimedEvent event =
                new OutboxPublishService.ClaimedEvent(
                        eventId,
                        "topic",
                        """
                                {
                                  "eventId": "%s",
                                  "eventType": "%s",
                                  "aggregateId": "%s"
                                }
                                """.formatted(eventId, eventType, assetId),
                        Instant.parse("2026-09-19T00:00:00Z")
                );
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OutboxPublishMonitor monitor = new OutboxPublishMonitor(
                outboxPublishService,
                meterRegistry,
                1
        );
        OutboxPublishScheduler scheduler = new OutboxPublishScheduler(
                outboxPublishService,
                outboxKafkaPublisher,
                new ObjectMapper(),
                Runnable::run,
                Runnable::run,
                monitor,
                meterRegistry
        );
        ReflectionTestUtils.setField(scheduler, "batchSize", 1);

        when(outboxPublishService.claimPendingEvents(1))
                .thenReturn(List.of(event));
        when(outboxKafkaPublisher.publish(event, assetId.toString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
        when(outboxPublishService.markPublished(event)).thenReturn(true);

        scheduler.publishPendingEvents();

        verify(outboxKafkaPublisher).publish(event, assetId.toString());
        verify(outboxPublishService).markPublished(event);
    }
}
