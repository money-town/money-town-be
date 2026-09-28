package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.DeadLetterReason;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.application.FinalSettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementReconciliationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinalSettlementQueryController.class)
@Import(GlobalExceptionHandler.class)
class FinalSettlementQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinalSettlementQueryService finalSettlementQueryService;

    @Test
    @DisplayName("최종 정산 회차 상태 조회에 성공하면 200과 함께 상세 정보를 반환한다")
    void getFinalSettlementBatch_success_returns200() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        FinalSettlementBatchDetailResponse response = new FinalSettlementBatchDetailResponse(
                finalSettlementBatchId, UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"), 1_000L, 100_000L,
                SettlementStatus.COMPLETED, new FinalSettlementBatchDetailResponse.Progress(10, 10, 0, 0, 0),
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"));
        when(finalSettlementQueryService.getFinalSettlementBatch("ADMIN", finalSettlementBatchId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/final-settlements/{finalSettlementBatchId}", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalSettlementBatchId").value(finalSettlementBatchId.toString()))
                .andExpect(jsonPath("$.data.progress.totalCount").value(10));
    }

    @Test
    @DisplayName("최종 정산 회차를 찾을 수 없으면 404를 반환한다")
    void getFinalSettlementBatch_notFound_returns404() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        when(finalSettlementQueryService.getFinalSettlementBatch("ADMIN", finalSettlementBatchId))
                .thenThrow(new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));

        mockMvc.perform(get("/api/v1/final-settlements/{finalSettlementBatchId}", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_404_04"));
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 403을 반환한다")
    void getFinalSettlementBatch_accessDenied_returns403() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        when(finalSettlementQueryService.getFinalSettlementBatch("INVESTOR", finalSettlementBatchId))
                .thenThrow(new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/final-settlements/{finalSettlementBatchId}", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "INVESTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_403_02"));
    }

    @Test
    @DisplayName("회차별 반환 내역 조회는 상태 필터를 서비스에 그대로 전달하고 페이지 응답을 반환한다")
    void getPayouts_withStatusFilter_delegatesToServiceAndReturnsPage() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        FinalSettlementPayoutListItemResponse item = new FinalSettlementPayoutListItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 10_000L, PayoutStatus.DEAD_LETTER, 2,
                DeadLetterReason.RESPONSE_MISMATCH, true);
        PageResponse<FinalSettlementPayoutListItemResponse> page =
                new PageResponse<>(List.of(item), 0, 20, 1, 1, true, true, false);
        when(finalSettlementQueryService.getPayouts(
                eq("ADMIN"), eq(finalSettlementBatchId), eq(PayoutStatus.DEAD_LETTER), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/final-settlements/{finalSettlementBatchId}/payouts", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN")
                        .param("status", "DEAD_LETTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].finalSettlementPayoutId").value(item.finalSettlementPayoutId().toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.data.content[0].deadLetterReason").value("RESPONSE_MISMATCH"))
                .andExpect(jsonPath("$.data.content[0].manualResolutionRequired").value(true));
    }

    @Test
    @DisplayName("상태 필터를 생략하면 status=null로 서비스를 호출한다")
    void getPayouts_withoutStatusFilter_passesNullStatusToService() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        PageResponse<FinalSettlementPayoutListItemResponse> page =
                new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, false);
        when(finalSettlementQueryService.getPayouts(eq("ADMIN"), eq(finalSettlementBatchId), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/final-settlements/{finalSettlementBatchId}/payouts", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("최종 정산 회차 정합성 검증에 성공하면 200과 함께 대사 결과를 반환한다")
    void getReconciliation_success_returns200() throws Exception {
        UUID finalSettlementBatchId = UUID.randomUUID();
        FinalSettlementReconciliationResponse response =
                FinalSettlementReconciliationResponse.of(finalSettlementBatchId, 100_000L, 90_000L, 90_000L);
        when(finalSettlementQueryService.getReconciliation("ADMIN", finalSettlementBatchId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/final-settlements/{finalSettlementBatchId}/reconciliation", finalSettlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(false))
                .andExpect(jsonPath("$.data.totalPayoutAmount").value(90_000));
    }
}