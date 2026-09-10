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

    @Value("${outbox.publish.batch-size:10}")
    private int batchSize;

    public OutboxPublishScheduler(
            OutboxPublishService outboxPublishService,
            OutboxKafkaPublisher outboxKafkaPublisher,
            ObjectMapper objectMapper,
            @Qualifier("outboxPublishCallbackExecutor")
            Executor outboxPublishCallbackExecutor
    ) {
        this.outboxPublishService = outboxPublishService;
        this.outboxKafkaPublisher = outboxKafkaPublisher;
        this.objectMapper = objectMapper;
        this.outboxPublishCallbackExecutor =
                outboxPublishCallbackExecutor;
    }

    @Scheduled(fixedDelayString = "${outbox.publish.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxPublishService.ClaimedEvent> events;

        try {
            events = outboxPublishService.claimPendingEvents(batchSize);
        } catch (Exception e) {
            log.error("Outbox 이벤트 선점 실패", e);
            return;
        }

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
        final String messageKey;

        try {
            messageKey = resolveMessageKey(event);
        } catch (Exception e) {
            // Kafka 전송 전에 실패했으므로 명확한 발행 실패로 기록한다.
            recordFailure(event, e);
            return;
        }

        final CompletableFuture<SendResult<String, String>> publishFuture;

        try {
            /*
             * KafkaTemplate.send() 자체도 잘못된 topic이나 producer 상태 등에
             * 의해 Future를 반환하기 전에 예외를 던질 수 있다.
             */
            publishFuture =
                    outboxKafkaPublisher.publish(event, messageKey);

            if (publishFuture == null) {
                throw new IllegalStateException(
                        "Kafka 발행 Future가 null입니다."
                );
            }
        } catch (Exception e) {
            recordFailure(event, e);
            return;
        }

        try {
            /*
             * handleAsync를 사용하여 Kafka 실패도 콜백 안에서 소비한다.
             * 스케줄러 스레드는 Kafka ACK를 기다리지 않는다.
             */
            publishFuture
                    .handleAsync(
                            (result, failure) -> {
                                handlePublishCompletion(
                                        event,
                                        failure
                                );
                                return null;
                            },
                            outboxPublishCallbackExecutor
                    )
                    .exceptionally(callbackFailure -> {
                        /*
                         * executor 거절 등으로 완료 처리 자체가 실행되지 않은 경우다.
                         * Kafka 성공 여부를 확정할 수 없으므로 Outbox 상태는
                         * PROCESSING으로 유지하고 복구 스케줄러에 맡긴다.
                         */
                        log.error(
                                "Outbox 발행 완료 처리 실행 실패. "
                                        + "복구 대기. eventId={}",
                                event.eventId(),
                                unwrapCompletionFailure(callbackFailure)
                        );
                        return null;
                    });
        } catch (Exception e) {
            /*
             * 이미 Kafka 전송 요청을 넘긴 뒤이므로 실패 상태로 변경하면
             * 실제 성공 이벤트가 중복 발행될 수 있다.
             */
            log.error(
                    "Outbox 발행 완료 콜백 등록 실패. "
                            + "복구 대기. eventId={}",
                    event.eventId(),
                    e
            );
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