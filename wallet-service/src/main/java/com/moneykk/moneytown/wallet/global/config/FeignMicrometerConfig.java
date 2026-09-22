package com.moneykk.moneytown.wallet.global.config;

import feign.Capability;
import feign.micrometer.MicrometerCapability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;

/**
 * FeignClient 자식 컨텍스트에서 MicrometerCapability 자동 생성이 타이밍 이슈로 누락되는
 * 문제 우회용(spring-cloud-openfeign#881). {@code @EnableFeignClients(defaultConfiguration = ...)}로 등록.
 */
public class FeignMicrometerConfig {

    @Bean
    public Capability micrometerCapability(MeterRegistry meterRegistry) {
        return new MicrometerCapability(meterRegistry);
    }
}
