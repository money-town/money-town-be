package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.user.entity.RefreshToken;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.event.UserAccountEventWriter;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import com.moneykk.moneytown.user.global.security.jwt.JwtTokenProvider;
import com.moneykk.moneytown.user.repository.RefreshTokenRepository;
import com.moneykk.moneytown.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserAccountEventWriter userAccountEventWriter;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("로그아웃 시 사용자의 활성 Refresh Token을 모두 폐기한다")
    void logoutRevokesActiveRefreshTokens() {
        UUID userId = UUID.randomUUID();
        User user = User.create(
                "hong@example.com",
                "encoded-password",
                "홍길동",
                "01012345678"
        );
        RefreshToken refreshToken = RefreshToken.create(
                userId,
                new IssuedToken(
                        "refresh-token",
                        Instant.now().plusSeconds(3600),
                        UUID.randomUUID().toString()
                )
        );

        given(userRepository.findByUserIdAndIsDeletedFalse(userId))
                .willReturn(Optional.of(user));
        given(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId))
                .willReturn(List.of(refreshToken));

        authService.logout(userId);

        assertThat(refreshToken.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("회원가입 시 같은 트랜잭션 흐름에서 UserRegistered 이벤트를 기록한다")
    void signupRecordsUserRegisteredEvent() {
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        SignupRequest request = new SignupRequest(
                "new@example.com",
                "Password123!",
                "신규 사용자",
                "01012345678"
        );

        given(passwordEncoder.encode(request.password()))
                .willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "userId", userId);
                    return user;
                });

        authService.signup(request, correlationId);

        then(userAccountEventWriter)
                .should()
                .recordRegistered(userId, correlationId);
    }
}
