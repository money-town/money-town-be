package com.moneykk.moneytown.offering.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class OfferingKafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler offeringKafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new FixedBackOff(
                        3_000L,
                        FixedBackOff.UNLIMITED_ATTEMPTS
                )
        );

        // 기본 비재시도 예외 분류를 교체한다.
        // DLT 정책 마련 전에는 검증·역직렬화 오류도 건너뛰지 않는다.
        errorHandler.setClassifications(
                Map.<Class<? extends Throwable>, Boolean>of(),
                true
        );

        return errorHandler;
    }
}