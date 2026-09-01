package com.moneykk.moneytown.common.config;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static com.moneykk.moneytown.common.security.AuthHeaderConstants.USER_ID;

@AutoConfiguration
@ConditionalOnClass(EnableJpaAuditing.class)
@EnableJpaAuditing
public class JpaAuditingConfig {

    // 스케줄러/내부 처리 등 HTTP 요청 컨텍스트가 없는 경로에서 사용하는 약속된 SYSTEM 식별자.
    // created_by/updated_by가 NOT NULL이므로 이 값이 항상 채워지도록 empty를 반환하지 않는다.
    public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (!(requestAttributes instanceof ServletRequestAttributes attributes)) {
                return Optional.of(SYSTEM_USER_ID);
            }

            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader(USER_ID);
            if (userId == null || userId.isBlank()) {
                return Optional.of(SYSTEM_USER_ID);
            }

            try {
                return Optional.of(UUID.fromString(userId));
            } catch (IllegalArgumentException e) {
                return Optional.of(SYSTEM_USER_ID);
            }
        };
    }
}