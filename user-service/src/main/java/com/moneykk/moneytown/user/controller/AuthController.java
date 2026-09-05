package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.user.dto.request.LoginRequest;
import com.moneykk.moneytown.user.dto.request.ReissueRequest;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.response.LoginResponse;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.dto.response.TokenResponse;
import com.moneykk.moneytown.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    // TODO 토큰 재발급 API 구현

    // 로그인
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request){


        return ApiResponse.success(authService.login(request)
        ,"로그인 성공");

    }

    // 로그아웃
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId
    ) {
        authService.logout(userId);
        return ApiResponse.success(null, "로그아웃 성공");
    }

    // 회원가입
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request){


        return ApiResponse.success(authService.signup(request),
                "회원가입 성공");
    }


    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(
            @Valid @RequestBody ReissueRequest request
    ) {
        return ApiResponse.success(
                authService.reissue(request),
                "토큰 재발급 성공"
        );
    }






}
