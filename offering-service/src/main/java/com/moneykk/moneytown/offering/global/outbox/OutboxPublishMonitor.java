package com.moneykk.moneytown.offering.global.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class OutboxPublishMonitor {

    // Kafka 응답을 기다리는 이벤트 수까지 제한하기 위한 Semaphore
    private final Semaphore publishSlots;

    // 현재 애플리케이션 인스턴스에서 발행 처리 중인 이벤트 수
    private final AtomicInteger inFlightCount =
            new AtomicInteger();

    // DB에 남아 있는 PROCESSING 이벤트 수를 메트릭으로 보관
    private final AtomicLong processingEventCount =
            new AtomicLong();

    private final Timer successLatency;
    private final Timer failureLatency;
    private final OutboxPublishService outboxPublishService;

    public OutboxPublishMonitor(
            OutboxPublishService outboxPublishService,
            MeterRegistry meterRegistry,
            @Value("${outbox.publish.max-in-flight:20}")
            int maxInFlight
    ) {
        if (maxInFlight <= 0) {
            throw new IllegalArgumentException(
                    "maxInFlight는 1 이상이어야 합니다."
            );
        }

        this.outboxPublishService =
                Objects.requireNonNull(outboxPublishService);

        this.publishSlots = new Semaphore(maxInFlight);

        // 현재 Kafka 발행 응답을 기다리고 있는 이벤트 수
        Gauge.builder(
                        "outbox.publish.inflight",
                        inFlightCount,
                        AtomicInteger::get
                )
                .description(
                        "현재 Kafka 발행 처리 중인 Outbox 이벤트 수"
                )
                .register(meterRegistry);

        // DB에서 PROCESSING 상태인 Outbox 이벤트 수
        Gauge.builder(
                        "outbox.events.processing",
                        processingEventCount,
                        AtomicLong::get
                )
                .description(
                        "DB에서 PROCESSING 상태인 Outbox 이벤트 수"
                )
                .register(meterRegistry);

        // Kafka 발행 성공까지 걸린 시간
        this.successLatency = Timer.builder(
                        "outbox.publish.latency"
                )
                .description("Outbox Kafka 발행 처리 시간")
                .tag("result", "success")
                .register(meterRegistry);

        // Kafka 발행 실패가 확인될 때까지 걸린 시간
        this.failureLatency = Timer.builder(
                        "outbox.publish.latency"
                )
                .description("Outbox Kafka 발행 처리 시간")
                .tag("result", "failure")
                .register(meterRegistry);
    }

    /**
     * 실제 DB에서 이벤트를 선점하기 전에 발행 슬롯을 확보한다.
     *
     * 확보한 슬롯 수만큼만 claimPendingEvents()를 호출해야 한다.
     */
    public int reserveSlots(int requestedCount) {
        if (requestedCount <= 0) {
            throw new IllegalArgumentException(
                    "requestedCount는 1 이상이어야 합니다."
            );
        }

        int reservedCount = 0;

        while (reservedCount < requestedCount
                && publishSlots.tryAcquire()) {
            reservedCount++;
        }

        inFlightCount.addAndGet(reservedCount);

        return reservedCount;
    }

    /**
     * DB 조회 결과가 확보한 슬롯보다 적을 때 남는 슬롯을 반환한다.
     */
    public void releaseUnusedSlots(int count) {
        releaseSlots(count);
    }

    /**
     * 이벤트별 발행 시간 측정을 시작한다.
     *
     * reserveSlots()로 확보한 이벤트에 대해서만 호출한다.
     */
    public PublishAttempt startAttempt() {
        return new PublishAttempt(System.nanoTime());
    }

    /**
     * Kafka 발행 성공을 기록하고 슬롯을 반환한다.
     */
    public void completeSuccess(PublishAttempt attempt) {
        complete(attempt, successLatency);
    }

    /**
     * Kafka 발행 실패를 기록하고 슬롯을 반환한다.
     */
    public void completeFailure(PublishAttempt attempt) {
        complete(attempt, failureLatency);
    }

    private void complete(
            PublishAttempt attempt,
            Timer latencyTimer
    ) {
        Objects.requireNonNull(attempt, "attempt는 필수입니다.");

        // 콜백과 예외 처리 양쪽에서 호출돼도 슬롯을 한 번만 반환
        if (!attempt.completeOnce()) {
            return;
        }

        long elapsedNanos = Math.max(
                0L,
                System.nanoTime() - attempt.startedNanos()
        );

        try {
            latencyTimer.record(
                    Duration.ofNanos(elapsedNanos)
            );
        } finally {
            releaseSlots(1);
        }
    }

    private void releaseSlots(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "반환할 슬롯 수는 음수일 수 없습니다."
            );
        }

        if (count == 0) {
            return;
        }

        int remaining = inFlightCount.addAndGet(-count);

        if (remaining < 0) {
            inFlightCount.addAndGet(count);

            throw new IllegalStateException(
                    "확보한 수보다 많은 Outbox 발행 슬롯을 반환했습니다."
            );
        }

        publishSlots.release(count);
    }

    /**
     * DB의 PROCESSING 건수를 주기적으로 갱신한다.
     *
     * 실제 쿼리는 다음 단계에서 OutboxPublishService에 추가한다.
     */
    @Scheduled(
            initialDelayString =
                    "${outbox.metrics.refresh-delay-ms:5000}",
            fixedDelayString =
                    "${outbox.metrics.refresh-delay-ms:5000}"
    )
    public void refreshProcessingEventCount() {
        try {
            processingEventCount.set(
                    outboxPublishService.countProcessingEvents()
            );
        } catch (Exception e) {
            // 조회 실패 시 직전 메트릭 값을 유지한다.
            log.warn(
                    "Outbox PROCESSING 건수 메트릭 갱신 실패",
                    e
            );
        }
    }

    /**
     * 동일 이벤트의 슬롯이 중복 반환되는 것을 방지한다.
     */
    public static final class PublishAttempt {

        private final long startedNanos;
        private final AtomicBoolean completed =
                new AtomicBoolean(false);

        private PublishAttempt(long startedNanos) {
            this.startedNanos = startedNanos;
        }

        private long startedNanos() {
            return startedNanos;
        }

        private boolean completeOnce() {
            return completed.compareAndSet(false, true);
        }
    }
}
