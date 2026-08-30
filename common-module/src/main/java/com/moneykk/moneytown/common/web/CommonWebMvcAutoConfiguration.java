package com.moneykk.moneytown.common.web;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// gateway-service(WebFlux)에는 적용되지 않도록 가드한다.
@AutoConfiguration(before = SpringDataWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SpringDataWebProperties.class)
public class CommonWebMvcAutoConfiguration implements WebMvcConfigurer {

    private final PageSizeLimitArgumentResolver pageableResolver;

    public CommonWebMvcAutoConfiguration(SpringDataWebProperties properties) {
        SpringDataWebProperties.Pageable pageableProps = properties.getPageable();

        PageSizeLimitArgumentResolver resolver = new PageSizeLimitArgumentResolver();
        resolver.setPageParameterName(pageableProps.getPageParameter());
        resolver.setSizeParameterName(pageableProps.getSizeParameter());
        resolver.setOneIndexedParameters(pageableProps.isOneIndexedParameters());
        resolver.setPrefix(pageableProps.getPrefix());
        resolver.setQualifierDelimiter(pageableProps.getQualifierDelimiter());
        resolver.setFallbackPageable(PageRequest.of(0, pageableProps.getDefaultPageSize()));
        resolver.setMaxPageSize(pageableProps.getMaxPageSize());

        this.pageableResolver = resolver;
    }

    @Bean
    @ConditionalOnMissingBean(PageableHandlerMethodArgumentResolver.class)
    public PageableHandlerMethodArgumentResolver pageableResolver() {
        return pageableResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(pageableResolver);
    }
}