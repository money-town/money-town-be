package com.moneykk.moneytown.settlement.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Settlement Service API",
                description = "수익/배당 정산, 최종 정산(원금반환) API",
                version = "v1"
        ),
        // 게이트웨이가 이 문서를 집계해서 서빙하므로, "Try it out" 요청도 문서를 연 origin(게이트웨이)으로 나가도록 상대경로로 고정한다.
        servers = @Server(url = "/", description = "API Gateway"),
        // bearerAuth 스킴 정의는 common-module의 CommonOpenApiAutoConfiguration에서 공통으로 제공한다.
        security = @SecurityRequirement(name = "bearerAuth")
)
public class OpenApiConfig {
}