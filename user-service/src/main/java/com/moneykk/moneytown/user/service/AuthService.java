package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.LoginRequest;
import com.moneykk.moneytown.user.dto.request.ReissueRequest;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.response.LoginResponse;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.dto.response.TokenResponse;
import com.moneykk.moneytown.user.entity.RefreshToken;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import com.moneykk.moneytown.user.global.security.jwt.JwtTokenProvider;
import com.moneykk.moneytown.user.repository.RefreshTokenRepository;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;


    // 로그인
    @Transactional
    public LoginResponse login(LoginRequest request){
        User user = userRepository
                .findByEmailAndIsDeletedFalse(request.email())
                .orElseThrow(()
                        -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if(!passwordEncoder.matches(request.password(), user.getPassword())){
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);

        }

        if(user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.ACCOUNT_UNAVAILABLE);
        }

        IssuedToken accessToken =
                jwtTokenProvider.issueAccessToken(user);

        IssuedToken refreshToken =
                jwtTokenProvider.issueRefreshToken(user);

        revokeActiveRefreshTokens(user.getUserId());
        refreshTokenRepository.save(
                RefreshToken.create(user.getUserId(), refreshToken)
        );

        return LoginResponse.from(user,
                                accessToken,
                                refreshToken);
    }

    // 해당 사용자의 모든 Refresh Token 폐기
    @Transactional
    public void logout(UUID userId) {
        userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new BusinessException(UserErrorCode.USER_NOT_FOUND)
                );

        revokeActiveRefreshTokens(userId);
    }

    // 회원가입
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateDuplicateEmail(request.email());
        validateDuplicatePhone(request.phone());


        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.create(request.email(),
                encodedPassword,
                request.name(),
                request.phone());

        User savedUser = userRepository.save(user);


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

    private void revokeActiveRefreshTokens(UUID userId) {
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(RefreshToken::revoke);
    }


    @Transactional
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

        // 4. DB에 저장된 Refresh Token 조회
        RefreshToken savedToken = refreshTokenRepository
                .findByTokenIdForUpdate(tokenId)
                .orElseThrow(() ->
                        new BusinessException(
                                AuthErrorCode.INVALID_REFRESH_TOKEN
                        )
                );

        // 5. 토큰 소유자·폐기·만료 상태 확인
        validateSavedRefreshToken(savedToken, userId);

        // 6. 최신 사용자 상태 확인
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

        // 7. 기존 Refresh Token 폐기
        savedToken.revoke();

        // 8. 새로운 Token 발급
        IssuedToken newAccessToken =
                jwtTokenProvider.issueAccessToken(user);

        IssuedToken newRefreshToken =
                jwtTokenProvider.issueRefreshToken(user);

        // 9. 새로운 Refresh Token JTI 저장
        refreshTokenRepository.save(
                RefreshToken.create(
                        user.getUserId(),
                        newRefreshToken
                )
        );

        // 10. 새로운 Token 응답
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

    private void validateSavedRefreshToken(
            RefreshToken refreshToken,
            UUID userId
    ) {
        boolean differentUser =
                !refreshToken.getUserId().equals(userId);

        boolean revoked =
                refreshToken.getRevokedAt() != null;

        boolean expired =
                refreshToken.getExpiresAt()
                        .isBefore(Instant.now());

        if (differentUser || revoked || expired) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
            );
        }
    }
}
