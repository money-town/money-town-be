package com.moneykk.moneytown.asset.global.security;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalApiInterceptorTest {

    private final InternalApiInterceptor interceptor =
            new InternalApiInterceptor();

    private final HttpServletRequest request =
            mock(HttpServletRequest.class);

    private final HttpServletResponse response =
            mock(HttpServletResponse.class);

    @Test
    @DisplayName("SYSTEM 권한은 내부 API 호출을 통과한다")
    void allowsSystemRole() {
        when(request.getHeader(AuthHeaderConstants.USER_ROLE))
                .thenReturn("SYSTEM");

        boolean allowed = interceptor.preHandle(
                request,
                response,
                new Object()
        );

        assertTrue(allowed);
    }

    @Test
    @DisplayName("SYSTEM 외 권한은 내부 API 호출을 거부한다")
    void rejectsNonSystemRole() {
        when(request.getHeader(AuthHeaderConstants.USER_ROLE))
                .thenReturn("ADMIN");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interceptor.preHandle(
                        request,
                        response,
                        new Object()
                )
        );

        assertEquals(
                AssetErrorCode.INTERNAL_API_ACCESS_DENIED,
                exception.getErrorCode()
        );
    }

    @Test
    @DisplayName("권한 헤더가 없으면 내부 API 호출을 거부한다")
    void rejectsMissingRole() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interceptor.preHandle(
                        request,
                        response,
                        new Object()
                )
        );

        assertEquals(
                AssetErrorCode.INTERNAL_API_ACCESS_DENIED,
                exception.getErrorCode()
        );
    }
}
