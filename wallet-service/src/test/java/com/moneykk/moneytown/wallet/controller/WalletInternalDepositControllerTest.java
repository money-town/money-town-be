package com.moneykk.moneytown.wallet.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletInternalDepositController.class)
@Import(GlobalExceptionHandler.class)
class WalletInternalDepositControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    private static final UUID INVESTOR_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();

    @Test
    @DisplayName("배당금 입금 성공 시 201을 반환한다")
    void depositDividend_success_returns201() throws Exception {
        DividendDepositResponse response = new DividendDepositResponse(
                1L, 1L, "DIVIDEND", 10_000L, BATCH_ID, Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.depositDividend(any(), anyString(), any(), anyLong())).thenReturn(response);

        mockMvc.perform(post("/api/v1/internal/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "idem-1", "investorId": "%s", "settlementBatchId": "%s", "amount": 10000}
                                """.formatted(INVESTOR_ID, BATCH_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("DIVIDEND"));
    }

    @Test
    @DisplayName("배당금 입금 시 필수값이 없으면 400(COMMON_400)을 반환한다")
    void depositDividend_missingField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/internal/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investorId\": \"%s\", \"amount\": 10000}".formatted(INVESTOR_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("같은 키로 다른 배당 요청이 오면 409(WALLET_409_01)를 반환한다")
    void depositDividend_idempotencyKeyConflict_returns409() throws Exception {
        when(walletService.depositDividend(any(), anyString(), any(), anyLong()))
                .thenThrow(new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT));

        mockMvc.perform(post("/api/v1/internal/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "idem-1", "investorId": "%s", "settlementBatchId": "%s", "amount": 10000}
                                """.formatted(INVESTOR_ID, BATCH_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_409_01"));
    }

    @Test
    @DisplayName("자산종료 정산금 입금 성공 시 201을 반환한다")
    void depositSettlement_success_returns201() throws Exception {
        SettlementDepositResponse response = new SettlementDepositResponse(
                2L, 1L, "SETTLEMENT", 50_000L, BATCH_ID, Instant.parse("2026-09-10T09:00:00Z"));
        when(walletService.depositSettlement(any(), anyString(), any(), anyLong())).thenReturn(response);

        mockMvc.perform(post("/api/v1/internal/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "idem-2", "investorId": "%s", "finalSettlementBatchId": "%s", "amount": 50000}
                                """.formatted(INVESTOR_ID, BATCH_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("SETTLEMENT"));
    }

    @Test
    @DisplayName("정산금 입금 시 금액이 0 이하면 400(COMMON_400)을 반환한다")
    void depositSettlement_invalidAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/internal/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey": "idem-2", "investorId": "%s", "finalSettlementBatchId": "%s", "amount": -1}
                                """.formatted(INVESTOR_ID, BATCH_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}
