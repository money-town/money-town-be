package com.moneykk.moneytown.settlement.command.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryRequest;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinalSettlementCommandController.class)
@Import(GlobalExceptionHandler.class)
class FinalSettlementCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FinalSettlementCommandService finalSettlementCommandService;

    @MockitoBean
    private FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Test
    @DisplayName("새로 생성된 최종 정산 회차는 201과 함께 비동기 지급을 트리거한다")
    void openFinalSettlement_newlyCreated_returns201AndTriggersDisbursement() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID finalSettlementBatchId = UUID.randomUUID();
        OpenFinalSettlementRequest request = new OpenFinalSettlementRequest(assetId, Instant.parse("2026-09-01T00:00:00Z"), 1_000L);
        FinalSettlementBatchResponse response =
                new FinalSettlementBatchResponse(finalSettlementBatchId, assetId, 100_000L, SettlementStatus.CALCULATED, true);
        when(finalSettlementCommandService.openFinalSettlement(eq("SYSTEM"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/internal/final-settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "SYSTEM")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.finalSettlementBatchId").value(finalSettlementBatchId.toString()))
                .andExpect(jsonPath("$.data.newlyCreated").value(true));

        verify(finalSettlementDisbursementService).disburseAsync(finalSettlementBatchId);
    }

    @Test
    @DisplayName("이미 존재하는 회차를 멱등 반환하면(newlyCreated=false) 비동기 지급을 다시 트리거하지 않는다")
    void openFinalSettlement_idempotentExisting_doesNotTriggerDisbursementAgain() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID finalSettlementBatchId = UUID.randomUUID();
        OpenFinalSettlementRequest request = new OpenFinalSettlementRequest(assetId, Instant.parse("2026-09-01T00:00:00Z"), 1_000L);
        FinalSettlementBatchResponse response =
                new FinalSettlementBatchResponse(finalSettlementBatchId, assetId, 100_000L, SettlementStatus.DISBURSING, false);
        when(finalSettlementCommandService.openFinalSettlement(eq("SYSTEM"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/internal/final-settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "SYSTEM")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.newlyCreated").value(false));

        verify(finalSettlementDisbursementService, never()).disburseAsync(any());
    }

    @Test
    @DisplayName("unitPrice가 0 이하면 서비스 호출 없이 400을 반환한다")
    void openFinalSettlement_nonPositiveUnitPrice_returns400WithoutCallingService() throws Exception {
        OpenFinalSettlementRequest request =
                new OpenFinalSettlementRequest(UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"), 0L);

        mockMvc.perform(post("/api/v1/internal/final-settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "SYSTEM")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(finalSettlementDisbursementService, never()).disburseAsync(any());
    }

    @Test
    @DisplayName("SYSTEM 권한이 아니면 서비스가 던진 BusinessException을 403으로 매핑한다")
    void openFinalSettlement_systemAccessDenied_returns403() throws Exception {
        OpenFinalSettlementRequest request =
                new OpenFinalSettlementRequest(UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"), 1_000L);
        when(finalSettlementCommandService.openFinalSettlement(eq("ADMIN"), any()))
                .thenThrow(new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_SYSTEM_ACCESS_DENIED));

        mockMvc.perform(post("/api/v1/internal/final-settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_403_03"));
    }

    @Test
    @DisplayName("재처리 대상 ID를 지정한 재시도 요청은 200과 함께 재처리 건수를 반환한다")
    void retryFinalSettlement_withRequestBody_returns200() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        FinalSettlementRetryRequest request = new FinalSettlementRetryRequest(java.util.List.of(UUID.randomUUID()));
        FinalSettlementRetryResponse response =
                new FinalSettlementRetryResponse(finalSettlementBatchId, 1, SettlementStatus.DISBURSING);
        when(finalSettlementCommandService.retryFinalSettlement(eq("ADMIN"), eq(finalSettlementBatchId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/final-settlements/{finalSettlementBatchId}/retry", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retriedCount").value(1))
                .andExpect(jsonPath("$.message").value("실패 건 재처리가 시작되었습니다."));

        verify(finalSettlementDisbursementService).disburseAsync(finalSettlementBatchId);
    }

    @Test
    @DisplayName("요청 본문 없이 재시도해도 기본값으로 처리되어 200을 반환한다")
    void retryFinalSettlement_withoutRequestBody_returns200() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        FinalSettlementRetryResponse response =
                new FinalSettlementRetryResponse(finalSettlementBatchId, 3, SettlementStatus.DISBURSING);
        when(finalSettlementCommandService.retryFinalSettlement(
                eq("ADMIN"), eq(finalSettlementBatchId), eq(new FinalSettlementRetryRequest(null))))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/final-settlements/{finalSettlementBatchId}/retry", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retriedCount").value(3));
    }

    @Test
    @DisplayName("재처리 가능한 실패 건이 없으면 409를 반환한다")
    void retryFinalSettlement_noRetryablePayouts_returns409() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        when(finalSettlementCommandService.retryFinalSettlement(eq("ADMIN"), eq(finalSettlementBatchId), any()))
                .thenThrow(new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_NO_RETRYABLE_PAYOUTS));

        mockMvc.perform(post("/api/v1/final-settlements/{finalSettlementBatchId}/retry", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_409_09"));

        verify(finalSettlementDisbursementService, never()).disburseAsync(any());
    }
}