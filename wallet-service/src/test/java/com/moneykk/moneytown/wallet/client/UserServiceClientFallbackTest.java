package com.moneykk.moneytown.wallet.client;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceClientFallbackTest {

    @Test
    @DisplayName("서킷이 열리면 회원 서비스 상태를 확인할 수 없다는 예외를 던진다")
    void getInvestmentEligibility_throwsUserServiceUnavailable() {
        UserServiceClientFallback fallback = new UserServiceClientFallback();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fallback.getInvestmentEligibility(UUID.randomUUID()));

        assertEquals(WalletErrorCode.USER_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
}
