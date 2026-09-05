package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingHistoryListResponse;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HoldingHistoryControllerTest {

    @Mock
    private HoldingQueryService holdingQueryService;

    @InjectMocks
    private HoldingHistoryController holdingHistoryController;

    @Test
    @DisplayName("지분 이력 조회 요청에 기본 페이지 설정을 적용한다")
    void getsHoldingHistoriesWithDefaults() throws Exception {
        UUID holdingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        HoldingHistoryListResponse response =
                new HoldingHistoryListResponse(
                        holdingId,
                        List.of(),
                        null,
                        false
                );
        when(holdingQueryService.getHoldingHistories(
                holdingId,
                userId,
                "INVESTOR",
                null,
                20,
                Sort.Direction.DESC
        )).thenReturn(response);
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(holdingHistoryController)
                .build();

        mvc.perform(get("/api/v1/holdings/{holdingId}/histories", holdingId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "INVESTOR"))
                .andExpect(status().isOk());

        verify(holdingQueryService).getHoldingHistories(
                holdingId,
                userId,
                "INVESTOR",
                null,
                20,
                Sort.Direction.DESC
        );
    }
}
