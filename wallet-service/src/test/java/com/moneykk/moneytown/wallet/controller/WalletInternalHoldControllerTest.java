package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.wallet.dto.response.WalletHoldStatusResponse;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GlobalExceptionHandler는 common-module의 auto-configuration으로만 등록돼있어서
// @WebMvcTest 슬라이스에는 자동으로 안 실려 명시적으로 import한다.
@WebMvcTest(WalletInternalHoldController.class)
@Import(GlobalExceptionHandler.class)
class WalletInternalHoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    @DisplayName("Hold가 있으면 200과 함께 상태를 반환한다")
    void getWalletHoldStatus_holdExists_returns200() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        WalletHoldStatusResponse response = new WalletHoldStatusResponse(
                subscriptionId, 1_000L, WalletHoldStatus.HELD, Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.getWalletHoldStatus(subscriptionId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/internal/wallet-holds/{subscriptionId}", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subscriptionId").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.data.amount").value(1_000))
                .andExpect(jsonPath("$.data.status").value("HELD"));
    }

    @Test
    @DisplayName("Hold가 없으면 404를 반환한다")
    void getWalletHoldStatus_holdNotFound_returns404() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        when(walletService.getWalletHoldStatus(subscriptionId))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_HOLD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/internal/wallet-holds/{subscriptionId}", subscriptionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("WALLET_404_02"));
    }

    @Test
    @DisplayName("subscriptionId가 UUID 형식이 아니면 400을 반환한다")
    void getWalletHoldStatus_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/internal/wallet-holds/{subscriptionId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
