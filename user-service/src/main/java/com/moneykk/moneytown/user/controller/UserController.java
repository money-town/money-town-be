package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.user.dto.request.AdminUpdateUserRequest;
import com.moneykk.moneytown.user.dto.request.SignupRequest;
import com.moneykk.moneytown.user.dto.request.UpdateMyInfoRequest;
import com.moneykk.moneytown.user.dto.response.SignupResponse;
import com.moneykk.moneytown.user.dto.response.UserListResponse;
import com.moneykk.moneytown.user.dto.response.UserResponse;
import com.moneykk.moneytown.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.ws.rs.PATCH;
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


    // 내 정보 조회
    @GetMapping("/users/me")
    public ApiResponse<UserResponse> getUserMe(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId){


        return ApiResponse.success(userService.getUser(userId),
                "내 정보 조회 성공");
    }


    @PatchMapping("/users/me")
    public ApiResponse<UserResponse> updateUser(@RequestHeader(AuthHeaderConstants.USER_ID) UUID userId
            ,@Valid @RequestBody UpdateMyInfoRequest request){

        return ApiResponse.success(userService.updateUser(userId, request),
                "수정 완료");

    }

    @DeleteMapping("/users/me")
    public ApiResponse<Void> deleteUser(@RequestHeader(AuthHeaderConstants.USER_ID)
                                            UUID userId){
        userService.deleteUser(userId);

        return ApiResponse.success(null,
                "삭제 완료");
    }

    // 관리자

    // 사용자 단건 조회
    @GetMapping("users/{userId}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID userId){
        return ApiResponse.success(userService.getUser(userId),
                "사용자 단건 조회 성공");
    
    }

    // TODO : 관리자가 사용자의 수정 및 탈퇴

    // 관리자 단건 수정
    @PatchMapping("/users/{userId}")
    public ApiResponse<UserResponse> updateUserByAdmin(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ){
        return ApiResponse.success(userService.updateUserByAdmin(userId, request),
                "사용자 정보 수정 성공");

    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUserByAdmin(@RequestHeader(AuthHeaderConstants.USER_ID)
            UUID adminId,
            @PathVariable("userId")
            UUID userId){
        userService.deleteUserByAdmin(adminId,userId);



        return ApiResponse.success(null,"사용자 탈퇴 처리 성공");
    }






} // Controller
