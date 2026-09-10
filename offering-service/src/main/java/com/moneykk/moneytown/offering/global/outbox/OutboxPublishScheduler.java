package com.moneykk.moneytown.offering.global.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.support.SendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class OutboxPublishScheduler {

    private final OutboxPublishService outboxPublishService;
    private final OutboxKafkaPublisher outboxKafkaPublisher;
    private final ObjectMapper objectMapper;
    private final Executor outboxPublishCallbackExecutor;
    private final OutboxPublishMonitor outboxPublishMonitor;

    @Value("${outbox.publish.batch-size:10}")
    private int batchSize;

    public OutboxPublishScheduler(
            OutboxPublishService outboxPublishService,
            OutboxKafkaPublisher outboxKafkaPublisher,
            ObjectMapper objectMapper,
            @Qualifier("outboxPublishCallbackExecutor")
            Executor outboxPublishCallbackExecutor,
            OutboxPublishMonitor outboxPublishMonitor
    ) {
        this.outboxPublishService = outboxPublishService;
        this.outboxKafkaPublisher = outboxKafkaPublisher;
        this.objectMapper = objectMapper;
        this.outboxPublishCallbackExecutor = outboxPublishCallbackExecutor;
        this.outboxPublishMonitor = outboxPublishMonitor;
    }

    @Scheduled(fixedDelayString = "${outbox.publish.fixed-delay-ms:1000}")
    public void publishPendingEvents() {

        // Kafka 응답 대기 중인 이벤트까지 고려하여 발행 슬롯을 먼저 확보한다.
        final int reservedSlots;

        try {
            reservedSlots =
                    outboxPublishMonitor.reserveSlots(batchSize);
        } catch (Exception e) {
            log.error("Outbox 발행 슬롯 확보 실패", e);
            return;
        }

        // 동시 처리 한도에 도달했다면 DB 이벤트를 선점하지 않는다.
        if (reservedSlots == 0) {
            return;
        }

        List<OutboxPublishService.ClaimedEvent> events;

        try {
            // 확보한 슬롯 수만큼만 PROCESSING 상태로 선점한다.
            events = outboxPublishService.claimPendingEvents(
                    reservedSlots
            );
        } catch (Exception e) {
            // DB 선점 실패 시 미리 확보한 슬롯을 모두 반환한다.
            outboxPublishMonitor.releaseUnusedSlots(
                    reservedSlots
            );

            log.error("Outbox 이벤트 선점 실패", e);
            return;
        }

        // 조회된 이벤트가 슬롯 수보다 적으면 사용하지 않은 슬롯을 반환한다.
        int unusedSlots = reservedSlots - events.size();

        outboxPublishMonitor.releaseUnusedSlots(unusedSlots);

        for (OutboxPublishService.ClaimedEvent event : events) {
            publishClaimedEvent(event);
        }
    }

    /**
     * 선점한 Outbox 이벤트를 Kafka에 비동기로 발행한다.
     *
     * Kafka 완료 결과는 전용 executor에서 처리하여
     * 스케줄러 스레드가 broker 응답을 기다리지 않도록 한다.
     */
    private void publishClaimedEvent(
            OutboxPublishService.ClaimedEvent event
    ) {
        // 이벤트별 발행 시간 측정과 슬롯 중복 반환 방지에 사용한다.
        OutboxPublishMonitor.PublishAttempt attempt =
                outboxPublishMonitor.startAttempt();

        final String messageKey;

        try {
            messageKey = resolveMessageKey(event);
        } catch (Exception e) {
            try {
                recordFailure(event, e);
            } finally {
                // Kafka 전송 전 실패해도 확보한 슬롯을 반드시 반환한다.
                outboxPublishMonitor.completeFailure(attempt);
            }
            return;
        }

        final CompletableFuture<SendResult<String, String>> publishFuture;

        try {
            publishFuture =
                    outboxKafkaPublisher.publish(event, messageKey);

            if (publishFuture == null) {
                throw new IllegalStateException(
                        "Kafka 발행 Future가 null입니다."
                );
            }
        } catch (Exception e) {
            try {
                recordFailure(event, e);
            } finally {
                // KafkaTemplate.send()가 즉시 실패한 경우에도 슬롯을 반환한다.
                outboxPublishMonitor.completeFailure(attempt);
            }
            return;
        }

        try {
            publishFuture
                    .handleAsync(
                            (result, failure) -> {
                                try {
                                    handlePublishCompletion(
                                            event,
                                            failure
                                    );
                                } finally {

                                    // Kafka 완료 결과에 따라 지연 시간을 기록하고
                                    // 확보했던 슬롯을 반환한다.
                                    if (failure == null) {
                                        outboxPublishMonitor
                                                .completeSuccess(attempt);
                                    } else {
                                        outboxPublishMonitor
                                                .completeFailure(attempt);
                                    }
                                }

                                return null;
                            },
                            outboxPublishCallbackExecutor
                    )
                    .exceptionally(callbackFailure -> {
                        log.error(
                                "Outbox 발행 완료 처리 실행 실패. "
                                        + "복구 대기. eventId={}",
                                event.eventId(),
                                unwrapCompletionFailure(
                                        callbackFailure
                                )
                        );

                        // executor 거절이나 콜백 예외에도 슬롯을 반환한다.
                        // PublishAttempt가 중복 반환을 방지한다.
                        outboxPublishMonitor.completeFailure(
                                attempt
                        );

                        return null;
                    });
        } catch (Exception e) {
            log.error(
                    "Outbox 발행 완료 콜백 등록 실패. "
                            + "복구 대기. eventId={}",
                    event.eventId(),
                    e
            );

            // 콜백 등록 자체가 실패해도 슬롯이 영구 점유되지 않게 한다.
            outboxPublishMonitor.completeFailure(attempt);
        }
    }

    private void handlePublishCompletion(
            OutboxPublishService.ClaimedEvent event,
            Throwable failure
    ) {
        if (failure != null) {
            recordFailure(event, unwrapCompletionFailure(failure));
            return;
        }

        recordPublished(event);
    }

    private void recordPublished(
            OutboxPublishService.ClaimedEvent event
    ) {
        try {
            boolean updated =
                    outboxPublishService.markPublished(event);

            if (!updated) {
                log.warn(
                        "Outbox 발행 성공 결과 미반영: "
                                + "상태 또는 시도 변경. eventId={}",
                        event.eventId()
                );
            }
        } catch (Exception e) {
            /*
             * Kafka 발행은 성공했지만 DB 반영 여부는 불확실하므로
             * PROCESSING 복구 정책에 맡긴다.
             */
            log.error(
                    "Kafka 발행 성공 후 Outbox 결과 저장 실패. eventId={}",
                    event.eventId(),
                    e
            );
        }
    }

    private Throwable unwrapCompletionFailure(
            Throwable failure
    ) {
        if (failure instanceof CompletionException
                && failure.getCause() != null) {
            return failure.getCause();
        }

        return failure;
    }

    @Scheduled(fixedDelayString = "${outbox.publish.recovery-delay-ms:30000}")
    public void recoverExpiredEvents() {
        try {
            int recovered =
                    outboxPublishService.recoverExpiredProcessing(100);

            if (recovered > 0) {
                log.warn("처리 기한을 초과한 Outbox 이벤트 복구. count={}",
                        recovered);
            }
        } catch (Exception e) {
            log.error("Outbox PROCESSING 복구 실패", e);
        }
    }

    private String resolveMessageKey(
            OutboxPublishService.ClaimedEvent event
    ) throws Exception {
        JsonNode envelope = objectMapper.readTree(event.envelopeJson());

        if (envelope == null || !envelope.isObject()) {
            throw new IllegalArgumentException(
                    "Outbox 메시지가 JSON 객체가 아닙니다."
            );
        }

        UUID envelopeEventId =
                UUID.fromString(envelope.path("eventId").asText());

        if (!event.eventId().equals(envelopeEventId)) {
            throw new IllegalArgumentException(
                    "Outbox와 envelope의 eventId가 일치하지 않습니다."
            );
        }

        String eventType = envelope.path("eventType").asText();

        // userId를 Kafka 메시지 key로 사용하는 이벤트
        if (!"SubscriptionReserved".equals(eventType)
                && !"SubscriptionConfirmed".equals(eventType)
                && !"SubscriptionCompensationRequested".equals(eventType)
                && !"SubscriptionLimitExceeded".equals(eventType)
                && !"SubscriptionFailed".equals(eventType)) {
            throw new IllegalArgumentException(
                    "Kafka key 규칙이 정의되지 않은 이벤트입니다: " + eventType
            );
        }

        return UUID.fromString(
                envelope.path("userId").asText()
        ).toString();
    }

    private void recordFailure(
            OutboxPublishService.ClaimedEvent event,
            Throwable failure
    ) {
        log.warn("Outbox 발행 시도 실패. eventId={}", event.eventId(), failure);

        String lastError = failure.getClass().getSimpleName()
                + ": " + failure.getMessage();

        try {
            boolean updated =
                    outboxPublishService.markFailedAttempt(event, lastError);

            if (!updated) {
                log.warn("Outbox 실패 결과 미반영: 상태 또는 시도 변경. eventId={}",
                        event.eventId());
            }
        } catch (Exception e) {
            log.error("Outbox 발행 실패 결과 저장 실패. eventId={}",
                    event.eventId(), e);
        }
    }
}