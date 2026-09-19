package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.wallet.dto.response.AdminWalletDetailResponse;
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

@WebMvcTest(AdminWalletController.class)
@Import(GlobalExceptionHandler.class)
class AdminWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    @DisplayName("ADMIN 권한으로 조회하면 200과 지갑 상세 정보를 반환한다")
    void getWalletDetail_asAdmin_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminWalletDetailResponse response = new AdminWalletDetailResponse(
                1L, userId, 100_000L, 20_000L, 80_000L, false,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.getWalletDetail(1L, "ADMIN")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/wallets/{walletId}", 1L)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.walletId").value(1))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("ADMIN이 아니면 403(WALLET_403_02)을 반환한다")
    void getWalletDetail_notAdmin_returns403() throws Exception {
        when(walletService.getWalletDetail(1L, "INVESTOR"))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_ADMIN_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/admin/wallets/{walletId}", 1L)
                        .header(AuthHeaderConstants.USER_ROLE, "INVESTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WALLET_403_02"));
    }

    @Test
    @DisplayName("지갑이 없으면 404(WALLET_404_01)를 반환한다")
    void getWalletDetail_walletNotFound_returns404() throws Exception {
        when(walletService.getWalletDetail(999L, "ADMIN"))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/wallets/{walletId}", 999L)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_404_01"));
    }
}
