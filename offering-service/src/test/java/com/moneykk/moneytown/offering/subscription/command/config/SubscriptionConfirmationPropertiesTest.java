package com.moneykk.moneytown.offering.subscription.command.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionConfirmationPropertiesTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory()
                    .getValidator();

    @Test
    @DisplayName("기본 청약 확정 배치 설정은 검증을 통과한다")
    void acceptsDefaultConfiguration() {
        SubscriptionConfirmationProperties properties =
                new SubscriptionConfirmationProperties();

        assertThat(validator.validate(properties))
                .isEmpty();
    }

    @Test
    @DisplayName("청약 확정 배치 설정이 허용 범위를 벗어나면 거부한다")
    void rejectsOutOfRangeConfiguration() {
        SubscriptionConfirmationProperties properties =
                new SubscriptionConfirmationProperties();

        properties.setBatchSize(0);
        properties.setMaxBatchesPerRun(11);
        properties.setFixedDelayMs(99L);

        assertThat(validator.validate(properties))
                .extracting(violation ->
                        violation.getPropertyPath().toString()
                )
                .containsExactlyInAnyOrder(
                        "batchSize",
                        "maxBatchesPerRun",
                        "fixedDelayMs"
                );
    }
}
