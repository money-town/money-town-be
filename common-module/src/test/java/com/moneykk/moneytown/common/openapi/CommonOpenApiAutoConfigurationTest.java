package com.moneykk.moneytown.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
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
    @DisplayName("공통 인증 헤더를 Swagger Authorize 항목으로 등록한다")
    void registersGatewayHeaderSecuritySchemes() {
        OpenAPI openApi = new OpenAPI();

        configuration.gatewayHeaderSecuritySchemes().customise(openApi);

        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKeys("userId", "userRole");
    }

    @Test
    @DisplayName("API별 인증 헤더 입력란을 제거하고 보안 요구사항으로 변경한다")
    void replacesHeaderParametersWithSecurityRequirement() {
        Operation operation = new Operation().parameters(new ArrayList<>(
                List.of(
                        new Parameter().name("X-User-Id").in("header"),
                        new Parameter().name("X-User-Role").in("header"),
                        new Parameter().name("assetId").in("path")
                )
        ));

        configuration.gatewayHeaderOperationCustomizer()
                .customize(operation, null);

        assertThat(operation.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("assetId");
        assertThat(operation.getSecurity().get(0))
                .containsKeys("userId", "userRole");
    }
}
