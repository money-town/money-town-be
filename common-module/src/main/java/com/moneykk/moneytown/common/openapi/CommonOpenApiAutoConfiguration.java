package com.moneykk.moneytown.common.openapi;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

    static final String USER_ID_SCHEME = "userId";
    static final String USER_ROLE_SCHEME = "userRole";

    @Bean
    public OpenApiCustomizer gatewayHeaderSecuritySchemes() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }

            components
                    .addSecuritySchemes(
                            USER_ID_SCHEME,
                            apiKeyHeader(AuthHeaderConstants.USER_ID)
                    )
                    .addSecuritySchemes(
                            USER_ROLE_SCHEME,
                            apiKeyHeader(AuthHeaderConstants.USER_ROLE)
                    );
        };
    }

    @Bean
    public OperationCustomizer gatewayHeaderOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() == null) {
                return operation;
            }

            boolean requiresUserId = hasParameter(
                    operation,
                    AuthHeaderConstants.USER_ID
            );
            boolean requiresUserRole = hasParameter(
                    operation,
                    AuthHeaderConstants.USER_ROLE
            );

            // API마다 표시되던 인증 헤더 입력란을 제거한다.
            operation.getParameters().removeIf(parameter ->
                    isGatewayHeader(parameter.getName())
            );

            SecurityRequirement requirement = new SecurityRequirement();
            if (requiresUserId) {
                requirement.addList(USER_ID_SCHEME);
            }
            if (requiresUserRole) {
                requirement.addList(USER_ROLE_SCHEME);
            }
            if (!requirement.isEmpty()) {
                operation.addSecurityItem(requirement);
            }

            return operation;
        };
    }

    private static SecurityScheme apiKeyHeader(String headerName) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName);
    }

    private static boolean hasParameter(
            Operation operation,
            String parameterName
    ) {
        return operation.getParameters().stream()
                .anyMatch(parameter -> parameterName
                        .equalsIgnoreCase(parameter.getName()));
    }

    private static boolean isGatewayHeader(String parameterName) {
        return AuthHeaderConstants.USER_ID.equalsIgnoreCase(parameterName)
                || AuthHeaderConstants.USER_ROLE.equalsIgnoreCase(
                parameterName
        );
    }
}
