package com.moneykk.moneytown.common.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonOpenApiAutoConfigurationTest {

    private final CommonOpenApiAutoConfiguration configuration =
            new CommonOpenApiAutoConfiguration();

    @Test
    @DisplayName("API별 Gateway 인증 헤더 입력란을 제거한다")
    void hidesGatewayHeaderParameters() {
        Operation operation = new Operation().parameters(new ArrayList<>(
                List.of(
                        new Parameter().name("X-User-Id").in("header"),
                        new Parameter().name("X-User-Role").in("header"),
                        new Parameter().name("X-Correlation-Id").in("header"),
                        new Parameter().name("assetId").in("path")
                )
        ));

        configuration.gatewayHeaderOperationCustomizer()
                .customize(operation, null);

        assertThat(operation.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("assetId");
        assertThat(operation.getSecurity()).isNull();
    }
}
