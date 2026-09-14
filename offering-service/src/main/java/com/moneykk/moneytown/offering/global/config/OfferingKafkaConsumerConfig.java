package com.moneykk.moneytown.offering.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class OfferingKafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 3_000L;
    private static final long MAX_RETRY_ATTEMPTS = 3L;

    @Bean
    public DeadLetterPublishingRecoverer offeringDeadLetterPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(
                                record.topic() + ".DLT",
                                record.partition()
                        )
                );

        // DLT 발행 자체가 실패하면 원본 레코드를 정상 복구로 간주하지 않는다.
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setLogRecoveryRecord(true);

        return recoverer;
    }

    @Bean
    public DefaultErrorHandler offeringKafkaErrorHandler(
            DeadLetterPublishingRecoverer offeringDeadLetterPublishingRecoverer
    ) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                offeringDeadLetterPublishingRecoverer,
                new FixedBackOff(
                        RETRY_INTERVAL_MS,
                        MAX_RETRY_ATTEMPTS
                )
        );

        /*
         * 잘못된 JSON, 지원하지 않는 eventType, 잘못된 partition key 등은
         * 같은 레코드를 다시 처리해도 성공할 수 없으므로 즉시 DLT로 보낸다.
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
                        record.topic() + ".DLT",
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
}
