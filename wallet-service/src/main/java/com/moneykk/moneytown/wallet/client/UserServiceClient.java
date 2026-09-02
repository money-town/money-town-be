package com.moneykk.moneytown.wallet.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.client.dto.UserInvestmentEligibilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// Offering-User(은지-승욱) 협의로 만들어진 계약을 그대로 재사용한다 (wallet_openfeign_spec.md 1번 참고).
// 이 계약은 Wallet 소유가 아니므로, 실제 Path/필드가 바뀌면 이 문서와 함께 갱신할 것.
@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/internal/users/{userId}/investment-eligibility")
    ApiResponse<UserInvestmentEligibilityResponse> getInvestmentEligibility(
            @PathVariable("userId") UUID userId
    );
}
