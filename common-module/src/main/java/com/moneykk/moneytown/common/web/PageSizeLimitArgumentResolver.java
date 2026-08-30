package com.moneykk.moneytown.common.web;

import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// size는 10/30/50만 허용, 그 외 값은 10으로 보정.
public class PageSizeLimitArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);
    private static final int DEFAULT_SIZE = 10;

    private final PageableHandlerMethodArgumentResolver delegate = new PageableHandlerMethodArgumentResolver();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Pageable.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Pageable pageable = (Pageable) delegate.resolveArgument(parameter, mavContainer, webRequest, binderFactory);

        if (!ALLOWED_SIZES.contains(pageable.getPageSize())) {
            return PageRequest.of(pageable.getPageNumber(), DEFAULT_SIZE, pageable.getSort());
        }

        return pageable;
    }
}

