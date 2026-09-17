package com.moneykk.moneytown.settlement.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

// settlement-service의 첫 Kafka Consumer 재시도 정책.
// 메트릭 이름만 "settlement.kafka.consumer.dlt"로 바꿔 asset-service의 DLT 카운터와 대시보드에서 섞이지 않게 함
@Slf4j
@Configuration
public class SettlementKafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 3_000L;
    private static final long MAX_RETRY_COUNT = 3L;

    @Bean
    public DefaultErrorHandler settlementKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate
                );

        // DLT 발행 실패도 성공으로 처리하지 않음
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(
                                RETRY_INTERVAL_MS,
                                MAX_RETRY_COUNT
                        )
                );

        errorHandler.setRetryListeners(
                new RetryListener() {
                    @Override
                    public void failedDelivery(
                            ConsumerRecord<?, ?> record,
                            Exception exception,
                            int deliveryAttempt
                    ) {
                        log.warn(
                                "Kafka 이벤트 처리를 재시도합니다. topic={}, partition={}, offset={}, attempt={}",
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                deliveryAttempt
                        );
                    }

                    @Override
                    public void recovered(
                            ConsumerRecord<?, ?> record,
                            Exception exception
                    ) {
                        log.error(
                                "Kafka 이벤트가 DLT로 이동했습니다. topic={}, partition={}, offset={}",
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                exception
                        );

                        meterRegistry.counter(
                                "settlement.kafka.consumer.dlt",
                                "source_topic",
                                record.topic()
                        ).increment();
                    }
                }
        );

        return errorHandler;
    }
}