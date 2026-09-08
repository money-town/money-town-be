package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.service.HoldingCommandService;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminHoldingControllerTest {

    private HoldingCommandService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(HoldingCommandService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AdminHoldingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("비활성화된 관리자 지분 조정 API는 호출할 수 없다")
    void adjustmentApiIsDisabled() throws Exception {
        UUID holdingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/holdings/{holdingId}/adjustments", holdingId)
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetQuantity": 120,
                                  "reason": "운영자 수동 정정",
                                  "idempotencyKey": "ADJUSTMENT-001"
                                }
                                """))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }
}
