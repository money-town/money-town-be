package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.user.dto.request.AdminUpdateUserRequest;
import com.moneykk.moneytown.user.dto.request.UpdateMyInfoRequest;
import com.moneykk.moneytown.user.dto.response.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.user.dto.response.UserListResponse;
import com.moneykk.moneytown.user.dto.response.UserResponse;
import com.moneykk.moneytown.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "User",
        description = "사용자 조회·수정·탈퇴 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;



    @Operation(
            summary = "내 정보 조회",
            description = "JWT로 인증된 사용자의 정보를 조회",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/users/me")
    public ApiResponse<UserResponse> getUserMe(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId){


        return ApiResponse.success(userService.getUserMe(userId),
                "내 정보 조회 성공");
    }


    // 내 정보 수정
    @PatchMapping("/users/me")
    public ApiResponse<UserResponse> updateUser(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Valid @RequestBody UpdateMyInfoRequest request){

        return ApiResponse.success(userService.updateUser(userId, request),
                "수정 완료");

    }

    // 회원 탈퇴
    @DeleteMapping("/users/me")
    public ApiResponse<Void> deleteUser(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Parameter(hidden = true)
            @RequestHeader(
                    value = AuthHeaderConstants.CORRELATION_ID,
                    required = false
            ) String correlationId
    ) {
        userService.deleteUser(userId, correlationId);

        return ApiResponse.success(null,
                "삭제 완료");
    }

    // 관리자

    @Operation(
            summary = "사용자 목록 조회",
            description = "사용자 목록을 조회하거나 이름으로 검색. ADMIN 권한 필요",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/users")
    public ApiResponse<List<UserListResponse>> userList(@Parameter(
            description = "사용자 이름 검색어",
            example = "홍길동"
    )
            @RequestParam(name = "name", required = false) String name
    ){

        return ApiResponse.success(userService.userList(name),
                "사용자 목록 조회 성공"
        );
    }

    // 사용자 단건 조회
    @GetMapping("users/{userId}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID userId){
        return ApiResponse.success(userService.getUser(userId),
                "사용자 단건 조회 성공");
    
    }

   

    // 관리자 단건 수정
    @PatchMapping("/users/{userId}")
    public ApiResponse<UserResponse> updateUserByAdmin(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ){
        return ApiResponse.success(userService.updateUserByAdmin(userId, request),
                "사용자 정보 수정 성공");

    }

    // 관리자 사용자 탈퇴 처리
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUserByAdmin(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID adminId,
            @Parameter(hidden = true)
            @RequestHeader(
                    value = AuthHeaderConstants.CORRELATION_ID,
                    required = false
            ) String correlationId,
            @PathVariable("userId") UUID userId
    ) {
        userService.deleteUserByAdmin(adminId, userId, correlationId);



        return ApiResponse.success(null,"사용자 탈퇴 처리 성공");
    }







} // Controller
