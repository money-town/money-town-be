package com.moneykk.moneytown.analysis.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Analysis Service API",
                description = "FDS, AI 포트폴리오, 알림 API",
                version = "v1"
        ),
        // 게이트웨이가 이 문서를 집계해서 서빙하므로, "Try it out" 요청도 문서를 연 origin(게이트웨이)으로 나가도록 상대경로로 고정한다.
        servers = @Server(url = "/", description = "API Gateway"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}