package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.wallet.dto.response.CursorPageResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionListItemResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GlobalExceptionHandler는 common-module의 auto-configuration으로만 등록돼있어서
// @WebMvcTest 슬라이스에는 자동으로 안 실려 명시적으로 import한다.
@WebMvcTest(WalletController.class)
@Import(GlobalExceptionHandler.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("내 지갑 조회 성공 시 200과 잔액 정보를 반환한다")
    void getMyWallet_success_returns200() throws Exception {
        WalletResponse response = new WalletResponse(1L, 100_000L, 20_000L, 80_000L, Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.getMyWallet(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/me")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.walletId").value(1))
                .andExpect(jsonPath("$.data.balance").value(100_000))
                .andExpect(jsonPath("$.data.holdBalance").value(20_000))
                .andExpect(jsonPath("$.data.availableBalance").value(80_000));
    }

    @Test
    @DisplayName("지갑이 없으면 404(WALLET_404_01)를 반환한다")
    void getMyWallet_walletNotFound_returns404() throws Exception {
        when(walletService.getMyWallet(USER_ID))
                .thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        mockMvc.perform(get("/api/v1/wallets/me")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("WALLET_404_01"));
    }

    @Test
    @DisplayName("거래 내역 조회 성공 시 200과 커서 페이지를 반환한다")
    void getTransactions_success_returns200() throws Exception {
        TransactionListItemResponse item = new TransactionListItemResponse(
                1L, WalletTransactionType.DEPOSIT, 10_000L, 0L, 10_000L, null,
                Instant.parse("2026-09-10T09:00:00Z"));
        CursorPageResponse<TransactionListItemResponse> page =
                new CursorPageResponse<>(List.of(item), null, false);
        when(walletService.getTransactions(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/wallets/me/transactions")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("거래 내역 조회 시 type/size 파라미터가 서비스로 그대로 전달된다")
    void getTransactions_withFilters_passesParamsToService() throws Exception {
        when(walletService.getTransactions(eq(USER_ID), eq(WalletTransactionType.HOLD), any(), any(), eq("cursor-1"), eq(5)))
                .thenReturn(new CursorPageResponse<>(List.of(), null, false));

        mockMvc.perform(get("/api/v1/wallets/me/transactions")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .param("type", "HOLD")
                        .param("cursor", "cursor-1")
                        .param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("예치금 충전 성공 시 201을 반환한다")
    void deposit_success_returns201() throws Exception {
        TransactionResponse response = new TransactionResponse(
                1L, 1L, WalletTransactionType.DEPOSIT, 10_000L, 10_000L, Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.deposit(eq(USER_ID), eq("idem-key-1"), eq(10_000L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/wallets/me/deposits")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.amount").value(10_000));
    }

    @Test
    @DisplayName("충전 금액이 0 이하면 400(COMMON_400)을 반환한다")
    void deposit_invalidAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/me/deposits")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("충전 금액이 없으면 400(COMMON_400)을 반환한다")
    void deposit_missingAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/me/deposits")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다른 요청이 오면 409(WALLET_409_01)를 반환한다")
    void deposit_idempotencyKeyConflict_returns409() throws Exception {
        when(walletService.deposit(eq(USER_ID), anyString(), anyLong()))
                .thenThrow(new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT));

        mockMvc.perform(post("/api/v1/wallets/me/deposits")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_409_01"));
    }

    @Test
    @DisplayName("입출금 가능한 계정 상태가 아니면 403(WALLET_403_01)을 반환한다")
    void deposit_ineligibleForTransaction_returns403() throws Exception {
        when(walletService.deposit(eq(USER_ID), anyString(), anyLong()))
                .thenThrow(new BusinessException(WalletErrorCode.INELIGIBLE_FOR_TRANSACTION));

        mockMvc.perform(post("/api/v1/wallets/me/deposits")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WALLET_403_01"));
    }

    @Test
    @DisplayName("예치금 출금 성공 시 201을 반환한다")
    void withdraw_success_returns201() throws Exception {
        TransactionResponse response = new TransactionResponse(
                2L, 1L, WalletTransactionType.WITHDRAW, 5_000L, 5_000L, Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.withdraw(eq(USER_ID), eq("idem-key-2"), eq(5_000L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/wallets/me/withdrawals")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("WITHDRAW"));
    }

    @Test
    @DisplayName("가용잔액이 부족하면 400(WALLET_400_01)을 반환한다")
    void withdraw_insufficientBalance_returns400() throws Exception {
        when(walletService.withdraw(eq(USER_ID), anyString(), anyLong()))
                .thenThrow(new BusinessException(WalletErrorCode.INSUFFICIENT_AVAILABLE_BALANCE));

        mockMvc.perform(post("/api/v1/wallets/me/withdrawals")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "idem-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 999999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_400_01"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 비어있으면 400을 반환한다")
    void withdraw_blankIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallets/me/withdrawals")
                        .header(AuthHeaderConstants.USER_ID, USER_ID.toString())
                        .header("Idempotency-Key", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000}"))
                .andExpect(status().isBadRequest());
    }
}
