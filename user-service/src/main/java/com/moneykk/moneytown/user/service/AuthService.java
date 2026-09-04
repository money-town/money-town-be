package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.dto.request.LoginRequest;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.response.LoginResponse;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.AccountStatus;
import com.moneykk.moneytown.user.global.exception.AuthErrorCode;
import com.moneykk.moneytown.user.global.exception.UserErrorCode;
import com.moneykk.moneytown.user.global.security.jwt.JwtTokenProvider;
import com.moneykk.moneytown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    // 로그인
    public LoginResponse login(LoginRequest request){
        User user = userRepository
                .findByEmailAndIsDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if(!passwordEncoder.matches(request.password(), user.getPassword())){
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);

        }

        if(user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.ACCOUNT_UNAVAILABLE);
        }

        // TODO Access·Refresh Token 발급 및 로그인 응답 연결
        return LoginResponse.from(user);


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



}
