package com.moneykk.moneytown.user.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
                title = "User Service API",
                description = "사용자 인증, 사용자 관리, KYC API",
                version = "v1"),
        // 게이트웨이가 이 문서를 집계해서 서빙하므로, "Try it out" 요청도 문서를 연 origin(게이트웨이)으로 나가도록 상대경로로 고정한다.
        servers = @Server(url = "/", description = "API Gateway"),
        // bearerAuth 스킴 정의는 common-module의 CommonOpenApiAutoConfiguration에서 공통으로 제공한다.
        security = @SecurityRequirement(name = "bearerAuth")
)
public class OpenApiConfig {
}
