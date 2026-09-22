package com.moneykk.moneytown.offering.global.config;

import feign.Capability;
import feign.micrometer.MicrometerCapability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;

/**
 * Spring Cloud OpenFeign은 FeignClient별 자식 ApplicationContext에서
 * MicrometerCapability를 {@code @ConditionalOnBean(MeterRegistry.class)}로 자동 생성하는데,
 * 이 조건 평가가 실제 MeterRegistry 빈이 부모 컨텍스트에 만들어지기 전에 실행되어
 * Capability 빈 자체가 생성되지 않는 알려진 타이밍 문제가 있다.
 * (https://github.com/spring-cloud/spring-cloud-openfeign/issues/881)
 * {@code @EnableFeignClients(defaultConfiguration = ...)}로 모든 FeignClient 자식
 * 컨텍스트에 직접 전달해서 이 타이밍 문제를 우회한다.
 * 메인 컨텍스트 컴포넌트 스캔에 중복으로 걸리지 않도록 {@code @Configuration}은 붙이지 않는다.
 *
 * MicrometerObservationCapability(Observation 기반)는 로컬 검증 결과 이 프로젝트의
 * ObservationRegistry 배선에서 아무 메트릭도 만들지 않아 레거시 MicrometerCapability를 사용한다.
 * feign.Client/feign.Feign/feign.codec.* 네이밍으로 찍히며, client 대신 host 태그로
 * 대상 서비스를 구분한다. Grafana 패널 쿼리도 이 네이밍에 맞춰 작성해야 한다.
 */
public class FeignMicrometerConfig {

    @Bean
    public Capability micrometerCapability(MeterRegistry meterRegistry) {
        return new MicrometerCapability(meterRegistry);
    }
}
