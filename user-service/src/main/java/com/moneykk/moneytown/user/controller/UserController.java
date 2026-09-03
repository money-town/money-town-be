package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.dto.response.UserListResponse;
import com.moneykk.moneytown.user.dto.response.UserResponse;
import com.moneykk.moneytown.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    //사용자 전체 조회
    @GetMapping("/users")
    public ApiResponse<List<UserListResponse>> userList(){

        return ApiResponse.success(userService.userList(),
                "사용자 목록 조회 성공"
        );
    }

    // 사용자 단건 조회
    @GetMapping("users/{userId}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID userId){
        return ApiResponse.success(userService.getUser(userId),
                "사용자 단건 조회 성공");
    
    }

    // 내 정보 조회
    @GetMapping("/users/me")
    public ApiResponse<UserResponse> getUserMe(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId){


        return ApiResponse.success(userService.getUser(userId),
                "내 정보 조회 성공");
    }

    // 회원 가입
    @PostMapping("/auth/signup")
    public ApiResponse<SignupResponse> signUp(@Valid @RequestBody SignupRequest request){

       return ApiResponse.success(userService.signup(request),
               "회원가입이 완료되었습니다.");
    }





} // Controller
