package com.moneykk.moneytown.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// JWT 인증/인가(Security 설정, 필터, 라우트별 role 규칙)는 담당 팀원이 별도로 설계·구현한다.
// 여기 남아있는 건 공통 응답 포맷(global.response)과 에러 처리 패턴(global.exception)뿐이다.
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}