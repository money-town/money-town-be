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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
        name = "Auth",
        description = "회원가입·로그인·토큰 관리 API"
)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 사용자를 인증하고 Access Token과 Refresh Token을 발급"
    )
    @SecurityRequirements // gateway에서 permitAll 이기 때문에 swagger 자물쇠 없이 표시되로록 설정
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request){

        return ApiResponse.success(authService.login(request),"로그인 성공");

    }

    @Operation(
            summary = "로그아웃",
            description = "사용자의 활성 Refresh Token 폐기",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId
    ) {
        authService.logout(userId);
        return ApiResponse.success(null, "로그아웃 성공");
    }

    @Operation(
            summary = "회원가입",
            description = "이메일과 사용자 정보를 이용해 회원가입"
    )
    @SecurityRequirements
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request,
            @RequestHeader(
                    value = AuthHeaderConstants.CORRELATION_ID,
                    required = false
            ) String correlationId
    ) {

        return ApiResponse.success(authService.signup(request, correlationId), "회원가입 성공");

    }

    @Operation(
            summary = "토큰 재발급",
            description = "유효한 Refresh Token을 검증하고 새로운 인증 토큰을 발급"
    )
    @SecurityRequirements
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(
            @Valid @RequestBody ReissueRequest request
    ) {

        return ApiResponse.success(authService.reissue(request), "토큰 재발급 성공");

    }
}
