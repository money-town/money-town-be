package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.LoginRequest;
import com.moneykk.moneytown.user.dto.response.LoginResponse;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.event.UserAccountEventWriter;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import com.moneykk.moneytown.user.global.security.jwt.JwtTokenProvider;
import com.moneykk.moneytown.user.monitoring.LoginMetrics;
import com.moneykk.moneytown.user.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private JwtDecoder jwtDecoder;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginPasswordVerifier loginPasswordVerifier;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserAccountEventWriter eventWriter;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LoginRequest request = new LoginRequest("login@example.com", "Password123!");
    private final User user = User.create(request.email(), "encoded-password", "테스트", "01012345678");
    private final IssuedToken access = token("access");
    private final IssuedToken refresh = token("refresh");
    private AuthService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "userId", UUID.randomUUID());
        service = new AuthService(jwtDecoder, userRepository, refreshTokenService, passwordEncoder,
                loginPasswordVerifier, jwtTokenProvider, eventWriter, new LoginMetrics(registry));
    }

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void successfulLoginPreservesTokensAndRecordsAllFourStages() {
        validPassword();
        issueTokens();

        LoginResponse response = service.login(request);

        assertThat(response.userId()).isEqualTo(user.getUserId());
        assertThat(response.accessToken()).isEqualTo(access.value());
        assertThat(response.refreshToken()).isEqualTo(refresh.value());
        verify(refreshTokenService).replaceActiveToken(user.getUserId(), refresh);
        for (String stage : new String[]{"user_lookup", "password_verify", "token_issue", "refresh_token_replace"}) {
            assertThat(count(stage, "success")).isEqualTo(1);
            assertThat(count(stage, "failure")).isZero();
        }
    }

    @Test
    void unknownUserStopsBeforePasswordVerification() {
        given(userRepository.findByEmailAndIsDeletedFalse(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

        assertThat(count("user_lookup", "failure")).isEqualTo(1);
        assertThat(count("password_verify", "success")).isZero();
        verifyNoInteractions(loginPasswordVerifier, jwtTokenProvider, refreshTokenService);
    }

    @Test
    void wrongPasswordIsRecordedAsFailureWithoutIssuingTokens() {
        given(userRepository.findByEmailAndIsDeletedFalse(request.email())).willReturn(Optional.of(user));
        given(loginPasswordVerifier.matches(request.password(), user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

        assertThat(count("password_verify", "failure")).isEqualTo(1);
        assertThat(count("password_verify", "success")).isZero();
        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void inactiveUserStillCannotLogin() {
        validPassword();
        ReflectionTestUtils.setField(user, "accountStatus", AccountStatus.WITHDRAWN);

        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(UserErrorCode.ACCOUNT_UNAVAILABLE));

        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void signingFailureIsNotCountedAsSuccessfulTokenIssuance() {
        validPassword();
        IllegalStateException failure = new IllegalStateException("Signing failure");
        given(jwtTokenProvider.issueAccessToken(user)).willThrow(failure);

        assertThatThrownBy(() -> service.login(request)).isSameAs(failure);

        assertThat(count("token_issue", "failure")).isEqualTo(1);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshPersistenceFailureIsPropagatedAndTimed() {
        validPassword();
        issueTokens();
        IllegalStateException failure = new IllegalStateException("Commit failure");
        doThrow(failure).when(refreshTokenService).replaceActiveToken(user.getUserId(), refresh);

        assertThatThrownBy(() -> service.login(request)).isSameAs(failure);

        assertThat(count("refresh_token_replace", "failure")).isEqualTo(1);
        assertThat(count("refresh_token_replace", "success")).isZero();
    }

    private void validPassword() {
        given(userRepository.findByEmailAndIsDeletedFalse(request.email())).willReturn(Optional.of(user));
        given(loginPasswordVerifier.matches(request.password(), user.getPassword())).willReturn(true);
    }

    private void issueTokens() {
        given(jwtTokenProvider.issueAccessToken(user)).willReturn(access);
        given(jwtTokenProvider.issueRefreshToken(user)).willReturn(refresh);
    }

    private long count(String stage, String outcome) {
        return registry.get(LoginMetrics.METRIC_NAME).tags("stage", stage, "outcome", outcome).timer().count();
    }

    private static IssuedToken token(String value) {
        return new IssuedToken(value, Instant.now().plusSeconds(3600), UUID.randomUUID().toString());
    }
}
