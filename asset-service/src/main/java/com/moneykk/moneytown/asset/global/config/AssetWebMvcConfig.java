package com.moneykk.moneytown.asset.global.config;

import com.moneykk.moneytown.asset.global.security.InternalApiInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Asset Service의 HTTP 인터셉터 설정
 */
@Configuration
@RequiredArgsConstructor
public class AssetWebMvcConfig implements WebMvcConfigurer {

    private final InternalApiInterceptor internalApiInterceptor;

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        // 모든 내부 API에 SYSTEM 권한 검사 적용
        registry.addInterceptor(internalApiInterceptor)
                .addPathPatterns("/api/v1/internal/**");
    }
}