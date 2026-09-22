package com.moneykk.moneytown.wallet.global.config;

import feign.micrometer.MicrometerCapability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;

/**
 * FeignClient 자식 컨텍스트에서 MicrometerCapability 자동 생성이 타이밍 이슈로 누락되는
 * 문제 우회용(spring-cloud-openfeign#881). {@code @EnableFeignClients(defaultConfiguration = ...)}로 등록.
 * 반환 타입을 구체 타입으로 선언해 Spring 자체 자동구성과의 중복 등록을 방지한다
 */
public class FeignMicrometerConfig {

    @Bean
    public MicrometerCapability micrometerCapability(MeterRegistry meterRegistry) {
        return new MicrometerCapability(meterRegistry);
    }
}
