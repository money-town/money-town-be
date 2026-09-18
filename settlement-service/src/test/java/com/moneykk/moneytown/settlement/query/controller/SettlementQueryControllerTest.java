package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.application.SettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.MyDividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementReconciliationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementQueryController.class)
@Import(GlobalExceptionHandler.class)
class SettlementQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;

    @Test
    @DisplayName("정산 회차 상태 조회에 성공하면 200과 함께 상세 정보를 반환한다")
    void getSettlementBatch_success_returns200() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        SettlementBatchDetailResponse response = new SettlementBatchDetailResponse(
                settlementBatchId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 10_000L,
                SettlementStatus.COMPLETED, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"),
                new SettlementBatchDetailResponse.PayoutSummary(10, 10, 0, 0));
        when(settlementQueryService.getSettlementBatch("ADMIN", settlementBatchId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/{settlementBatchId}", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.settlementBatchId").value(settlementBatchId.toString()))
                .andExpect(jsonPath("$.data.payoutSummary.totalCount").value(10))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("정산 회차를 찾을 수 없으면 404를 반환한다")
    void getSettlementBatch_notFound_returns404() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        when(settlementQueryService.getSettlementBatch("ADMIN", settlementBatchId))
                .thenThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        mockMvc.perform(get("/api/v1/settlements/{settlementBatchId}", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_404_03"));
    }

    @Test
    @DisplayName("ADMIN 권한이 아니면 403을 반환한다")
    void getSettlementBatch_accessDenied_returns403() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        when(settlementQueryService.getSettlementBatch("INVESTOR", settlementBatchId))
                .thenThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/settlements/{settlementBatchId}", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "INVESTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_403_01"));
    }

    @Test
    @DisplayName("회차별 지급 내역 조회는 상태 필터를 서비스에 그대로 전달하고 페이지 응답을 반환한다")
    void getPayouts_withStatusFilter_delegatesToServiceAndReturnsPage() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        DividendPayoutListItemResponse item = new DividendPayoutListItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(0.5), 5_000L, PayoutStatus.DEAD_LETTER, 2);
        PageResponse<DividendPayoutListItemResponse> page =
                new PageResponse<>(List.of(item), 0, 10, 1, 1, true, true, false);
        when(settlementQueryService.getPayouts(eq("ADMIN"), eq(settlementBatchId), eq(PayoutStatus.DEAD_LETTER), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/settlements/{settlementBatchId}/payouts", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN")
                        .param("status", "DEAD_LETTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].dividendPayoutId").value(item.dividendPayoutId().toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("상태 필터를 생략하면 status=null로 서비스를 호출한다")
    void getPayouts_withoutStatusFilter_passesNullStatusToService() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        PageResponse<DividendPayoutListItemResponse> page =
                new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, false);
        when(settlementQueryService.getPayouts(eq("ADMIN"), eq(settlementBatchId), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/settlements/{settlementBatchId}/payouts", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("정산 회차 정합성 검증에 성공하면 200과 함께 대사 결과를 반환한다")
    void getReconciliation_success_returns200() throws Exception {
        UUID settlementBatchId = UUID.randomUUID();
        SettlementReconciliationResponse response =
                SettlementReconciliationResponse.of(settlementBatchId, 10_000L, 10_000L, 9_000L);
        when(settlementQueryService.getReconciliation("ADMIN", settlementBatchId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/{settlementBatchId}/reconciliation", settlementBatchId)
                        .header(AuthHeaderConstants.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(true))
                .andExpect(jsonPath("$.data.paidAmount").value(9_000));
    }

    @Test
    @DisplayName("내 배당 내역 조회는 X-User-Id 헤더의 투자자 ID로 서비스를 호출한다")
    void getMyDividends_success_returns200() throws Exception {
        UUID investorId = UUID.randomUUID();
        MyDividendPayoutListItemResponse item = new MyDividendPayoutListItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1),
                BigDecimal.valueOf(0.1), 1_000L, PayoutStatus.PAID, Instant.parse("2026-09-02T00:00:00Z"));
        PageResponse<MyDividendPayoutListItemResponse> page =
                new PageResponse<>(List.of(item), 0, 20, 1, 1, true, true, false);
        when(settlementQueryService.getMyDividends(eq(investorId), isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/dividends/me")
                        .header("X-User-Id", investorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].dividendPayoutId").value(item.dividendPayoutId().toString()));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
    void getMyDividends_missingUserIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/dividends/me"))
                .andExpect(status().isBadRequest());
    }
}