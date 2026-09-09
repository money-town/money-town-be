package com.moneykk.moneytown.asset.global.security;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Asset 내부 API는 SYSTEM만 호출할 수 있도록 검사한다.
 */
@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String role = request.getHeader(
                AuthHeaderConstants.USER_ROLE
        );

        // SYSTEM이 아니거나 헤더가 없으면 내부 API 호출 거부
        if (!"SYSTEM".equals(role)) {
            throw new BusinessException(
                    AssetErrorCode.INTERNAL_API_ACCESS_DENIED
            );
        }

        return true;
    }
}