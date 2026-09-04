package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.LoginRequest;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.response.LoginResponse;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
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



}
