package com.moneykk.moneytown.offering.global.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublishScheduler {

    private final OutboxPublishService outboxPublishService;
    private final OutboxKafkaPublisher outboxKafkaPublisher;
    private final ObjectMapper objectMapper;

    @Value("${outbox.publish.batch-size:10}")
    private int batchSize;

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
            boolean continuePublishing = publishClaimedEvent(event);

            if (!continuePublishing) {
                return;
            }
        }
    }

    /**
     * 선점한 Outbox 이벤트 한 건을 발행하고 결과를 기록한다.
     *
     * @return 다음 이벤트 발행을 계속할 수 있으면 true,
     * 스레드 인터럽트로 중단해야 하면 false
     */
    private boolean publishClaimedEvent(
            OutboxPublishService.ClaimedEvent event
    ) {
        try {
            String messageKey = resolveMessageKey(event);

            outboxKafkaPublisher.publish(event, messageKey)
                    .get(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            /*
             * 애플리케이션 종료 등으로 스레드가 중단된 경우
             * 남은 이벤트는 PROCESSING 복구 스케줄러가 다시 처리한다.
             */
            log.warn(
                    "Outbox 발행 대기 중 인터럽트. eventId={}",
                    event.eventId()
            );
            return false;

        } catch (TimeoutException e) {
            /*
             * 대기 시간이 끝났어도 Kafka 전송은 진행 중일 수 있으므로
             * 실패로 확정하지 않고 PROCESSING 복구 대상으로 남긴다.
             */
            log.warn(
                    "Outbox 발행 결과 대기 시간 초과. 복구 대기. eventId={}",
                    event.eventId()
            );
            return true;

        } catch (ExecutionException e) {
            recordFailure(
                    event,
                    e.getCause() != null ? e.getCause() : e
            );
            return true;

        } catch (Exception e) {
            recordFailure(event, e);
            return true;
        }

        // Kafka 성공 후 DB 기록 실패를 전송 실패로 처리하지 않도록 분리한다.
        try {
            boolean updated = outboxPublishService.markPublished(event);

            if (!updated) {
                log.warn(
                        "Outbox 발행 성공 결과 미반영: 상태 또는 시도 변경. eventId={}",
                        event.eventId()
                );
            }
        } catch (Exception e) {
            log.error(
                    "Kafka 발행 성공 후 Outbox 결과 저장 실패. eventId={}",
                    event.eventId(),
                    e
            );
        }

        return true;
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
                && !"SubscriptionLimitExceeded".equals(eventType)) {
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