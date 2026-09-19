package com.moneykk.moneytown.offering.subscription.command.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "subscription.timeout")
public class SubscriptionTimeoutProperties {

    /**
     * 한 번의 키셋 조회에서 가져올 최대 청약 수.
     */
    @Min(1)
    @Max(500)
    private int batchSize = 100;

    /**
     * 한 번의 스케줄 실행에서 처리할 최대 키셋 배치 수.
     *
     * 기본값 기준 인스턴스당 한 번에 최대 200건을 처리한다.
     */
    @Min(1)
    @Max(10)
    private int maxBatchesPerRun = 2;
}