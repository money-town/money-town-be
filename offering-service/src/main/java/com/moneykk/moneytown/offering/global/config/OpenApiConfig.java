package com.moneykk.moneytown.offering.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Offering Service API",
                description = "공모 상품, 선착순 청약 API",
                version = "v1"
        ),
        // 게이트웨이가 이 문서를 집계해서 서빙하므로, "Try it out" 요청도 문서를 연 origin(게이트웨이)으로 나가도록 상대경로로 고정한다.
        servers = @Server(url = "/", description = "API Gateway"),
        // bearerAuth 스킴 정의는 common-module의 CommonOpenApiAutoConfiguration에서 공통으로 제공한다.
        security = @SecurityRequirement(name = "bearerAuth")
)
public class OpenApiConfig {
}