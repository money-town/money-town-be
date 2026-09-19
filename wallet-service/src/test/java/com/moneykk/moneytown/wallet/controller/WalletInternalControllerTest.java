package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.wallet.dto.response.WalletStatusResponse;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletInternalController.class)
@Import(GlobalExceptionHandler.class)
class WalletInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    @DisplayName("지갑 상태 조회 성공 시 200과 활성 Hold 여부를 반환한다")
    void getWalletStatus_success_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        WalletStatusResponse response = new WalletStatusResponse(1L, 20_000L, 80_000L, true);
        when(walletService.getWalletStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/internal/wallets/{userId}/status", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.walletId").value(1))
                .andExpect(jsonPath("$.data.hasActiveHold").value(true));
    }

    @Test
    @DisplayName("지갑이 없으면 404(WALLET_404_01)를 반환한다")
    void getWalletStatus_walletNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(walletService.getWalletStatus(userId))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        mockMvc.perform(get("/api/v1/internal/wallets/{userId}/status", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_404_01"));
    }

    @Test
    @DisplayName("userId가 UUID 형식이 아니면 400을 반환한다")
    void getWalletStatus_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/internal/wallets/{userId}/status", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
