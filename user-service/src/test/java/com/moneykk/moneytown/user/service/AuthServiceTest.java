package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.user.entity.RefreshToken;
import com.moneykk.moneytown.user.entity.User;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
}
