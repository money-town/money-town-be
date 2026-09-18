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
@ConfigurationProperties(prefix = "subscription.confirmation")
public class SubscriptionConfirmationProperties {

    @Min(1)
    @Max(500)
    private int batchSize = 100;

    @Min(1)
    @Max(10)
    private int maxBatchesPerRun = 2;

    @Min(100)
    @Max(60000)
    private long fixedDelayMs = 1000L;
}
