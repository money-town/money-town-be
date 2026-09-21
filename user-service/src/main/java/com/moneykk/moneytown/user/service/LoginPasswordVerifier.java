package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** CPU 집약적인 BCrypt 검증이 동시에 과도하게 실행되지 않도록 제한한다. */
@Component
public class LoginPasswordVerifier {
    private final PasswordEncoder passwordEncoder;
    private final Semaphore permits;
    private final Duration acquireTimeout;

    public LoginPasswordVerifier(
            PasswordEncoder passwordEncoder,
            @Value("${auth.login.password-verification.max-concurrency:2}") int maxConcurrency,
            @Value("${auth.login.password-verification.acquire-timeout:2s}") Duration acquireTimeout
    ) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("BCrypt 최대 동시 실행 수는 1 이상이어야 합니다.");
        }
        if (acquireTimeout.isNegative()) {
            throw new IllegalArgumentException("BCrypt 대기 시간은 0 이상이어야 합니다.");
        }
        this.passwordEncoder = passwordEncoder;
        this.permits = new Semaphore(maxConcurrency, true);
        this.acquireTimeout = acquireTimeout;
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new BusinessException(AuthErrorCode.LOGIN_BUSY);
            }
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(AuthErrorCode.LOGIN_BUSY);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }
}
