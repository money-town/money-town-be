package com.moneykk.moneytown.common.web;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

// logging.pattern.correlation의 %X{requestId:-} 자리를 채운다 (config-repository/application.yml).
// 게이트웨이가 이미 X-Correlation-Id를 채워서 넘겨주므로 여기서는 MDC에 옮겨 담기만 한다.
public class CorrelationIdMdcFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(AuthHeaderConstants.CORRELATION_ID);
        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(MDC_KEY, correlationId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}