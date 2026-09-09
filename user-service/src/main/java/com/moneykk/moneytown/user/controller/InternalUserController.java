package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.user.dto.response.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/users")
public class InternalUserController {
    private final UserService userService;

    @GetMapping("/{userId}/investment-eligibility")
    public ApiResponse<UserInvestmentEligibilityResponse>
    getInvestmentEligibility(@PathVariable UUID userId){
        return ApiResponse.success(userService.getInvestmentEligibility(userId),
                "사용자 최신 상태 조회 성공");
    }




}
