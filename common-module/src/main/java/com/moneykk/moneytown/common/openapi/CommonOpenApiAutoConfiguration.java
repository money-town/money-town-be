package com.moneykk.moneytown.common.openapi;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Swagger의 Gateway 인증 헤더를 공통으로 설정한다. */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonOpenApiAutoConfiguration {

    @Bean
    public OperationCustomizer gatewayHeaderOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() == null) {
                return operation;
            }

            // API마다 표시되던 인증 헤더 입력란을 제거한다.
            operation.getParameters().removeIf(parameter ->
                    isGatewayHeader(parameter.getName())
            );

            return operation;
        };
    }

    private static boolean isGatewayHeader(String parameterName) {
        return AuthHeaderConstants.USER_ID.equalsIgnoreCase(parameterName)
                || AuthHeaderConstants.USER_ROLE.equalsIgnoreCase(
                parameterName
        );
    }
}
