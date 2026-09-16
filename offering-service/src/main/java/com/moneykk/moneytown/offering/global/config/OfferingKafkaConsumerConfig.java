package com.moneykk.moneytown.offering.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Slf4j
@Configuration
public class OfferingKafkaConsumerConfig {

    private static final long INITIAL_RETRY_INTERVAL_MS = 1_000L;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long MAX_RETRY_INTERVAL_MS = 4_000L;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Bean
    public DeadLetterPublishingRecoverer
    offeringDeadLetterPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        /*
         * Spring Kafka의 기본 DLT 규칙을 사용한다.
         *
         * 토픽: {원본 토픽}-dlt
         * 파티션: 원본 레코드와 동일한 파티션
         */
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        // DLT 발행 자체가 실패하면 원본 레코드를 정상 복구로 간주하지 않는다.
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setLogRecoveryRecord(true);

        return recoverer;
    }

    @Bean
    public DefaultErrorHandler offeringKafkaErrorHandler(
            DeadLetterPublishingRecoverer
                    offeringDeadLetterPublishingRecoverer
    ) {
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        offeringDeadLetterPublishingRecoverer,
                        kafkaConsumerBackOff()
                );

        /*
         * 잘못된 JSON, 지원하지 않는 eventType,
         * 잘못된 partition key 등은 같은 레코드를 다시 처리해도
         * 성공할 수 없으므로 재시도 없이 즉시 DLT로 보낸다.
         */
        errorHandler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class
        );

        errorHandler.setRetryListeners(new RetryListener() {

            @Override
            public void failedDelivery(
                    ConsumerRecord<?, ?> record,
                    Exception exception,
                    int deliveryAttempt
            ) {
                log.warn(
                        "Kafka Consumer 처리 실패. "
                                + "topic={}, partition={}, offset={}, "
                                + "deliveryAttempt={}, exception={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        exception.getClass().getSimpleName()
                );
            }

            @Override
            public void recovered(
                    ConsumerRecord<?, ?> record,
                    Exception exception
            ) {
                log.error(
                        "Kafka Consumer 실패 레코드 DLT 전송 완료. "
                                + "topic={}, partition={}, offset={}, "
                                + "dltTopic={}, exception={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.topic() + "-dlt",
                        exception.getClass().getSimpleName()
                );
            }

            @Override
            public void recoveryFailed(
                    ConsumerRecord<?, ?> record,
                    Exception original,
                    Exception failure
            ) {
                log.error(
                        "Kafka Consumer DLT 전송 실패. "
                                + "topic={}, partition={}, offset={}, "
                                + "originalException={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        original.getClass().getSimpleName(),
                        failure
                );
            }
        });

        return errorHandler;
    }

    /*
     * 최초 처리 실패 후 1초, 2초, 4초 간격으로 재시도한다.
     * 세 번의 재시도 후에도 실패하면 DLT로 보낸다.
     *
     * package-private로 선언하여 실제 대기 없이
     * 단위 테스트에서 백오프 간격을 검증할 수 있게 한다.
     */
    ExponentialBackOffWithMaxRetries kafkaConsumerBackOff() {
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(
                        MAX_RETRY_ATTEMPTS
                );

        backOff.setInitialInterval(
                INITIAL_RETRY_INTERVAL_MS
        );
        backOff.setMultiplier(
                RETRY_MULTIPLIER
        );
        backOff.setMaxInterval(
                MAX_RETRY_INTERVAL_MS
        );

        return backOff;
    }
}