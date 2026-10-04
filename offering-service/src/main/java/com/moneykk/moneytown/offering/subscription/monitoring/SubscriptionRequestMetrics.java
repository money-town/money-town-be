package com.moneykk.moneytown.offering.subscription.monitoring;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;

@Component
public class SubscriptionRequestMetrics {

    private final SubscriptionRequestRepository repository;
    private final Counter accepted;
    private final Counter replayed;
    private final Counter recovered;
    private final Counter completed;
    private final Counter rejected;
    private final Counter failed;
    private final Timer processingDuration;
    private final Map<SubscriptionRequestStatus, AtomicLong> statusGauges =
            new EnumMap<>(SubscriptionRequestStatus.class);

    public SubscriptionRequestMetrics(
            MeterRegistry meterRegistry,
            SubscriptionRequestRepository repository
    ) {
        this.repository = repository;
        this.accepted = Counter.builder("subscription.request.accepted")
                .description("새로 저장된 비동기 청약 접수 수")
                .register(meterRegistry);
        this.replayed = Counter.builder("subscription.request.replayed")
                .description("멱등 재요청으로 재사용된 청약 접수 수")
                .register(meterRegistry);
        this.recovered = Counter.builder("subscription.request.recovered")
                .description("정체 상태에서 다시 큐에 적재된 청약 접수 수")
                .register(meterRegistry);
        this.completed = Counter.builder("subscription.request.completed")
                .description("Subscription 생성까지 완료된 비동기 접수 수")
                .register(meterRegistry);
        this.rejected = Counter.builder("subscription.request.rejected")
                .description("업무 규칙에 따라 거절된 비동기 접수 수")
                .register(meterRegistry);
        this.failed = Counter.builder("subscription.request.failed")
                .description("재시도 소진 후 DLT에서 종료된 비동기 접수 수")
                .register(meterRegistry);
        this.processingDuration = Timer.builder("subscription.request.processing.duration")
                .description("Kafka 청약 처리 시작부터 종료까지의 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);

        for (SubscriptionRequestStatus status : SubscriptionRequestStatus.values()) {
            AtomicLong value = new AtomicLong();
            statusGauges.put(status, value);
            meterRegistry.gauge(
                    "subscription.request.status",
                    java.util.List.of(
                            io.micrometer.core.instrument.Tag.of(
                                    "status", status.name().toLowerCase()
                            )
                    ),
                    value
            );
        }
    }

    public void recordAccepted() {
        accepted.increment();
    }

    public void recordReplayed() {
        replayed.increment();
    }

    public void recordRecovered(int count) {
        if (count > 0) {
            recovered.increment(count);
        }
    }

    public void recordCompleted() {
        completed.increment();
    }

    public void recordRejected() {
        rejected.increment();
    }

    public void recordFailed() {
        failed.increment();
    }

    public void recordProcessing(Duration duration) {
        processingDuration.record(duration);
    }

    @Scheduled(fixedDelayString =
            "${subscription.request.metrics-refresh-delay-ms:10000}")
    public void refreshStatusGauges() {
        for (SubscriptionRequestStatus status : SubscriptionRequestStatus.values()) {
            statusGauges.get(status).set(repository.countByRequestStatus(status));
        }
    }
}
