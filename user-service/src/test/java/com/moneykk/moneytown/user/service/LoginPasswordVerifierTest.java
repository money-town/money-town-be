package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginPasswordVerifierTest {

    @Test
    void rejectsExcessVerificationWithLoginBusy() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PasswordEncoder blockingEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                entered.countDown();
                try {
                    release.await();
                    return true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        };
        LoginPasswordVerifier verifier = new LoginPasswordVerifier(blockingEncoder, 1, Duration.ZERO);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = executor.submit(() -> verifier.matches("raw", "encoded"));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> verifier.matches("raw", "encoded"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_BUSY));

            release.countDown();
            assertThat(first.get()).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
