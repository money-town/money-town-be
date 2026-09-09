package com.moneykk.moneytown.user.global.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.user.event.UserAccountEventConstants;
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
        } catch (Exception exception) {
            log.error("Outbox 이벤트 선점 실패", exception);
            return;
        }

        for (OutboxPublishService.ClaimedEvent event : events) {
            if (!publish(event)) {
                return;
            }
        }
    }

    @Scheduled(fixedDelayString = "${outbox.publish.recovery-delay-ms:30000}")
    public void recoverExpiredEvents() {
        try {
            int recovered = outboxPublishService.recoverExpiredProcessing(100);
            if (recovered > 0) {
                log.warn("처리 기한 초과 Outbox 이벤트 복구. count={}", recovered);
            }
        } catch (Exception exception) {
            log.error("Outbox PROCESSING 복구 실패", exception);
        }
    }

    private boolean publish(OutboxPublishService.ClaimedEvent event) {
        try {
            outboxKafkaPublisher.publish(event, resolveMessageKey(event))
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Outbox 발행 대기 중 인터럽트. eventId={}", event.eventId());
            return false;
        } catch (TimeoutException exception) {
            log.warn("Outbox 발행 결과 대기 시간 초과. eventId={}", event.eventId());
            return true;
        } catch (ExecutionException exception) {
            recordFailure(
                    event,
                    exception.getCause() != null
                            ? exception.getCause()
                            : exception
            );
            return true;
        } catch (Exception exception) {
            recordFailure(event, exception);
            return true;
        }

        try {
            if (!outboxPublishService.markPublished(event)) {
                log.warn("Outbox 발행 결과 미반영. eventId={}", event.eventId());
            }
        } catch (Exception exception) {
            log.error(
                    "Kafka 발행 성공 후 Outbox 상태 저장 실패. eventId={}",
                    event.eventId(),
                    exception
            );
        }
        return true;
    }

    private String resolveMessageKey(
            OutboxPublishService.ClaimedEvent event
    ) throws Exception {
        JsonNode envelope = objectMapper.readTree(event.envelopeJson());

        if (envelope == null || !envelope.isObject()) {
            throw new IllegalArgumentException("Outbox payload가 JSON 객체가 아닙니다.");
        }

        UUID envelopeEventId = UUID.fromString(
                envelope.path("eventId").asText()
        );

        if (!event.eventId().equals(envelopeEventId)) {
            throw new IllegalArgumentException("Outbox eventId가 일치하지 않습니다.");
        }

        String eventType = envelope.path("eventType").asText();
        if (!UserAccountEventConstants.USER_REGISTERED.equals(eventType)
                && !UserAccountEventConstants.USER_WITHDRAWN.equals(eventType)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 User 이벤트입니다: " + eventType
            );
        }

        return UUID.fromString(envelope.path("userId").asText()).toString();
    }

    private void recordFailure(
            OutboxPublishService.ClaimedEvent event,
            Throwable failure
    ) {
        log.warn("Outbox 발행 실패. eventId={}", event.eventId(), failure);

        String message = failure.getClass().getSimpleName()
                + ": " + failure.getMessage();

        try {
            if (!outboxPublishService.markFailedAttempt(event, message)) {
                log.warn("Outbox 실패 결과 미반영. eventId={}", event.eventId());
            }
        } catch (Exception exception) {
            log.error(
                    "Outbox 실패 상태 저장 실패. eventId={}",
                    event.eventId(),
                    exception
            );
        }
    }
}
