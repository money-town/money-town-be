package com.moneykk.moneytown.asset.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Asset Kafka Consumer의 재시도 정책 */
@Configuration
public class AssetKafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 3_000L;
    private static final long MAX_RETRY_COUNT = 3L;

    @Bean
    public DefaultErrorHandler assetKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        // 최종 실패 메시지를 원래 토픽명.DLT로 보낸다.
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate
                );

        // 3초 간격으로 최대 3회 재시도한다.
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        RETRY_INTERVAL_MS,
                        MAX_RETRY_COUNT
                )
        );
    }
}