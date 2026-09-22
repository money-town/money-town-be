package com.moneykk.moneytown.settlement.global.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// Outbox 스케줄러 자체의 실패 횟수 (offering-service의 OfferingSchedulerMetrics 중 Outbox 몫과 같은 역할).
@Component
public class SettlementOutboxMetrics {

    private static final String METRIC_NAME = "settlement.scheduler.failure";

    private final Counter outboxPublishBatchFailure;
    private final Counter outboxRecoveryFailure;

    public SettlementOutboxMetrics(MeterRegistry meterRegistry) {
        this.outboxPublishBatchFailure = registerCounter(meterRegistry, "outbox-publish");
        this.outboxRecoveryFailure = registerCounter(meterRegistry, "outbox-recovery");
    }

    public void recordOutboxPublishBatchFailure() {
        outboxPublishBatchFailure.increment();
    }

    public void recordOutboxRecoveryFailure() {
        outboxRecoveryFailure.increment();
    }

    private Counter registerCounter(MeterRegistry meterRegistry, String scheduler) {
        return Counter.builder(METRIC_NAME)
                .description("Settlement Service 스케줄러 실패 횟수")
                .tag("scheduler", scheduler)
                .register(meterRegistry);
    }
}