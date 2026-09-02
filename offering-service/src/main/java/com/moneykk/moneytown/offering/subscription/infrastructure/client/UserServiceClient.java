package com.moneykk.moneytown.offering.subscription.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/internal/users/{userId}/investment-eligibility")
    ApiResponse<UserInvestmentEligibilityResponse> getInvestmentEligibility(
            @PathVariable("userId") UUID userId
    );
}