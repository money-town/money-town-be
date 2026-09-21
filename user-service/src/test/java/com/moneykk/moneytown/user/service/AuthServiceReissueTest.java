package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.ReissueRequest;
import com.moneykk.moneytown.user.dto.response.TokenResponse;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.event.UserAccountEventWriter;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceReissueTest {
    @Mock private JwtDecoder jwtDecoder;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginPasswordVerifier loginPasswordVerifier;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserAccountEventWriter eventWriter;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UUID userId = UUID.randomUUID();
    private final String currentTokenId = UUID.randomUUID().toString();
    private final ReissueRequest request = new ReissueRequest("current-refresh-token");
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                jwtDecoder,
                userRepository,
                refreshTokenService,
                passwordEncoder,
                loginPasswordVerifier,
                jwtTokenProvider,
                eventWriter,
                new LoginMetrics(registry)
        );
        given(jwtDecoder.decode(request.refreshToken())).willReturn(refreshJwt());
    }

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void rotatesCurrentRedisTokenAndReturnsNewTokens() {
        User user = User.create("user@example.com", "encoded", "사용자", "01012345678");
        ReflectionTestUtils.setField(user, "userId", userId);
        IssuedToken access = token("new-access");
        IssuedToken refresh = token("new-refresh");

        given(refreshTokenService.isActiveToken(userId, currentTokenId)).willReturn(true);
        given(userRepository.findByUserIdAndIsDeletedFalse(userId)).willReturn(Optional.of(user));
        given(jwtTokenProvider.issueAccessToken(user)).willReturn(access);
        given(jwtTokenProvider.issueRefreshToken(user)).willReturn(refresh);
        given(refreshTokenService.rotateActiveToken(userId, currentTokenId, refresh)).willReturn(true);

        TokenResponse response = service.reissue(request);

        assertThat(response.accessToken()).isEqualTo(access.value());
        assertThat(response.refreshToken()).isEqualTo(refresh.value());
        verify(refreshTokenService).rotateActiveToken(userId, currentTokenId, refresh);
    }

    @Test
    void rejectsTokenThatIsNotCurrentInRedis() {
        given(refreshTokenService.isActiveToken(userId, currentTokenId)).willReturn(false);

        assertThatThrownBy(() -> service.reissue(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

        verifyNoInteractions(userRepository, jwtTokenProvider);
    }

    private Jwt refreshJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue(request.refreshToken())
                .header("alg", "HS256")
                .issuer("money-town-user-service")
                .subject(userId.toString())
                .claim("jti", currentTokenId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("tokenType", "REFRESH")
                .build();
    }

    private static IssuedToken token(String value) {
        return new IssuedToken(value, Instant.now().plusSeconds(3600), UUID.randomUUID().toString());
    }
}
