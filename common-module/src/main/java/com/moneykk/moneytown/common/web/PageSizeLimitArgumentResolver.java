package com.moneykk.moneytown.common.web;

import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

public class PageSizeLimitArgumentResolver extends PageableHandlerMethodArgumentResolver {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);
    private static final int DEFAULT_SIZE = 10;

    public PageSizeLimitArgumentResolver(SortHandlerMethodArgumentResolver sortResolver) {
        super(sortResolver);
    }

    @Override
    public Pageable resolveArgument(MethodParameter methodParameter, ModelAndViewContainer mavContainer,
                                     NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);

        if (!ALLOWED_SIZES.contains(pageable.getPageSize())) {
            // 대체 크기가 상위 리졸버에 설정된 maxPageSize를 넘지 않도록 한다.
            int fallbackSize = Math.min(DEFAULT_SIZE, getMaxPageSize());
            return PageRequest.of(pageable.getPageNumber(), fallbackSize, pageable.getSort());
        }

        return pageable;
    }
}