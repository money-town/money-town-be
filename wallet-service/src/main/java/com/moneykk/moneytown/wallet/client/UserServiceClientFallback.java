package com.moneykk.moneytown.wallet.client;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.client.dto.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

// User 서비스 호출이 타임아웃/장애로 서킷이 열리면 실행됨. KYC 상태를 확인할 수 없는 상태이므로
// 통과시키지 않고 안전하게 거래를 막는다 (fail-safe, fail-open 아님).
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ApiResponse<UserInvestmentEligibilityResponse> getInvestmentEligibility(UUID userId) {
        throw new BusinessException(WalletErrorCode.USER_SERVICE_UNAVAILABLE);
    }
}
