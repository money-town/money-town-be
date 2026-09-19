package com.moneykk.moneytown.settlement.command.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.OpenSettlementRequest;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GlobalExceptionHandler는 common-module의 auto-configuration으로만 등록돼있어서
// @WebMvcTest 슬라이스에는 자동으로 안 실려 명시적으로 import한다.
@WebMvcTest(SettlementCommandController.class)
@Import(GlobalExceptionHandler.class)
class SettlementCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SettlementCommandService settlementCommandService;

    @MockitoBean
    private DividendDisbursementService dividendDisbursementService;

    @MockitoBean
    private RevenueTransferStatusNotifier revenueTransferStatusNotifier;

    @Test
    @DisplayName("정산 회차 개시에 성공하면 201과 함께 회차 정보를 반환하고, 수익 전달 통보와 비동기 지급을 트리거한다")
    void openSettlementBatch_success_returns201AndTriggersFollowUpActions() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        UUID settlementBatchId = UUID.randomUUID();
        SettlementBatchResponse response = new SettlementBatchResponse(
                settlementBatchId, assetId, revenueId, LocalDate.of(2026, 9, 1), 10_000L,
                SettlementStatus.CALCULATED, 3, Instant.parse("2026-09-01T00:00:00Z"));
        when(settlementCommandService.openBatch("ADMIN", assetId, revenueId, null)).thenReturn(response);

        mockMvc.perform(post("/api/v1/settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpenSettlementRequest(assetId, revenueId, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.settlementBatchId").value(settlementBatchId.toString()))
                .andExpect(jsonPath("$.data.payoutCount").value(3))
                .andExpect(jsonPath("$.message").value("정산 회차가 개시되었습니다."));

        verify(revenueTransferStatusNotifier).notifyTransferred(revenueId);
        verify(dividendDisbursementService).disburseAsync(settlementBatchId);
    }

    @Test
    @DisplayName("assetId·revenueId가 누락되면 서비스 호출 없이 400을 반환한다")
    void openSettlementBatch_missingRequiredFields_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(dividendDisbursementService, never()).disburseAsync(any());
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 서비스가 던진 BusinessException을 403으로 매핑한다")
    void openSettlementBatch_accessDenied_returns403() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        when(settlementCommandService.openBatch("ISSUER", assetId, revenueId, null))
                .thenThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED));

        mockMvc.perform(post("/api/v1/settlements")
                        .header(AuthHeaderConstants.USER_ROLE, "ISSUER")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpenSettlementRequest(assetId, revenueId, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_403_01"));

        verify(dividendDisbursementService, never()).disburseAsync(any());
        verify(revenueTransferStatusNotifier, never()).notifyTransferred(any());
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면 400을 반환한다")
    void openSettlementBatch_missingRoleHeader_returns400() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/settlements")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpenSettlementRequest(assetId, revenueId, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정산 회차 재시도에 성공하면 200과 함께 회차 정보를 반환하고, 비동기 지급을 재트리거한다")
    void retrySettlementBatch_success_returns200AndTriggersDisbursement() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        SettlementBatchResponse response = new SettlementBatchResponse(
                settlementBatchId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 10_000L,
                SettlementStatus.DISBURSING, 2, Instant.parse("2026-09-01T00:00:00Z"));
        when(settlementCommandService.retryBatch("ADMIN", settlementBatchId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/settlements/{settlementBatchId}/retry", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISBURSING"))
                .andExpect(jsonPath("$.message").value("정산 회차 재시도가 접수되었습니다."));

        verify(dividendDisbursementService).disburseAsync(settlementBatchId);
    }

    @Test
    @DisplayName("재시도 대상 회차를 찾을 수 없으면 404를 반환한다")
    void retrySettlementBatch_notFound_returns404() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        when(settlementCommandService.retryBatch("ADMIN", settlementBatchId))
                .thenThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        mockMvc.perform(post("/api/v1/settlements/{settlementBatchId}/retry", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_404_03"));

        verify(dividendDisbursementService, never()).disburseAsync(any());
    }

    @Test
    @DisplayName("settlementBatchId가 UUID 형식이 아니면 400을 반환한다")
    void retrySettlementBatch_invalidUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/{settlementBatchId}/retry", "not-a-uuid")
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isBadRequest());
    }
}