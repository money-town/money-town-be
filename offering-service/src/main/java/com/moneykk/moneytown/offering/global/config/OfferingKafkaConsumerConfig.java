package com.moneykk.moneytown.offering.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

        return errorHandler;
    }
}
