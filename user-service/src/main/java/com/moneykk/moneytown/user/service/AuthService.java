package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.LoginRequest;
import com.moneykk.moneytown.user.dto.request.ReissueRequest;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.response.LoginResponse;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.dto.response.TokenResponse;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.event.UserAccountEventWriter;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import com.moneykk.moneytown.user.global.security.jwt.JwtTokenProvider;
import com.moneykk.moneytown.user.monitoring.LoginMetrics;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.moneykk.moneytown.user.monitoring.LoginMetrics.Stage.*;


@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final LoginPasswordVerifier loginPasswordVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountEventWriter userAccountEventWriter;
    private final LoginMetrics loginMetrics;


    // 로그인
    public LoginResponse login(LoginRequest request){
        User user = loginMetrics.record(USER_LOOKUP, () -> userRepository
                .findByEmailAndIsDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS)));

        // DB 조회가 끝난 다음, 제한된 수의 BCrypt 검증만 동시에 실행한다.
        loginMetrics.record(PASSWORD_VERIFY, () -> {
            if (!loginPasswordVerifier.matches(request.password(), user.getPassword())) {
                throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
            }
        });

        if(user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.ACCOUNT_UNAVAILABLE);
        }

        LoginTokens tokens = loginMetrics.record(TOKEN_ISSUE, () -> new LoginTokens(
                jwtTokenProvider.issueAccessToken(user),
                jwtTokenProvider.issueRefreshToken(user)
        ));

        // Redis에 활성 Refresh Token jti를 저장하는 시간까지 측정한다.
        loginMetrics.record(REFRESH_TOKEN_REPLACE, () ->
                refreshTokenService.replaceActiveToken(user.getUserId(), tokens.refreshToken()));

        return LoginResponse.from(user, tokens.accessToken(), tokens.refreshToken());
    }

    private record LoginTokens(IssuedToken accessToken, IssuedToken refreshToken) {
    }

    // 해당 사용자의 모든 Refresh Token 폐기
    public void logout(UUID userId) {
        userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND)
                );

        refreshTokenService.revokeActiveToken(userId);
    }

    // 회원가입
    @Transactional
    public SignupResponse signup(
            SignupRequest request,
            String correlationId
    ) {
        validateDuplicateEmail(request.email());
        validateDuplicatePhone(request.phone());


        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.create(request.email(),
                encodedPassword,
                request.name(),
                request.phone());

        User savedUser = userRepository.save(user);

        userAccountEventWriter.recordRegistered(
                savedUser.getUserId(),
                correlationId
        );

        return SignupResponse.from(savedUser);
    }


    // 이메일 중복 방지
    private void validateDuplicateEmail( String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    UserErrorCode.EMAIL_ALREADY_EXISTS
            );
        }
    }

    // 휴대전화 번호 중복 방지
    private void validateDuplicatePhone(String phone) {
        if(userRepository.existsByPhone(phone)) {
            throw new BusinessException((
                    UserErrorCode.PHONE_ALREADY_EXISTS
            ));
        }
    }

    // 재발급
    public TokenResponse reissue(ReissueRequest request) {
        // 1. Refresh Token 서명·Issuer·만료 검증
        Jwt jwt = decodeRefreshToken(request.refreshToken());

        // 2. Refresh Token인지 확인
        validateRefreshTokenType(jwt);

        // 3. Claim에서 사용자 ID와 토큰 ID 추출
        UUID userId = parseUserId(jwt.getSubject());
        String tokenId = jwt.getId();

        if (tokenId == null || tokenId.isBlank()) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        // 4. Redis에 저장된 현재 활성 jti인지 확인
        if (!refreshTokenService.isActiveToken(userId, tokenId)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 5. 최신 사용자 상태 확인
        User user = userRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    UserErrorCode.ACCOUNT_UNAVAILABLE
            );
        }

        // 6. 새로운 Token 발급
        IssuedToken newAccessToken =
                jwtTokenProvider.issueAccessToken(user);

        IssuedToken newRefreshToken =
                jwtTokenProvider.issueRefreshToken(user);

        // 7. 기존 jti가 그대로일 때만 새 jti로 원자적으로 교체
        if (!refreshTokenService.rotateActiveToken(userId, tokenId, newRefreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 8. 새로운 Token 응답
        return TokenResponse.from(
                newAccessToken,
                newRefreshToken
        );

    }

    private Jwt decodeRefreshToken(String refreshToken) {
        try {
            return jwtDecoder.decode(refreshToken);
        } catch (JwtException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }
    }

    private void validateRefreshTokenType(Jwt jwt) {
        String tokenType =
                jwt.getClaimAsString(TOKEN_TYPE_CLAIM);

        if (!REFRESH_TOKEN_TYPE.equals(tokenType)) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }
    }

    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }
    }

}
