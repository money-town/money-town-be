package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.RevenueQueryRepository;
import com.moneykk.moneytown.asset.repository.RevenueRepository;
import com.moneykk.moneytown.asset.service.RevenueCommandService;
import com.moneykk.moneytown.asset.service.RevenueQueryService;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RevenueControllerTest {
    private final UUID revenueId = UUID.randomUUID();
    private final String url = "/api/v1/assets/revenues/" + revenueId + "/transfer-status";

    private MockMvc mvc(RevenueCommandService service) {
        return MockMvcBuilders.standaloneSetup(
                        new RevenueController(mock(RevenueQueryService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("SYSTEM 헤더를 서비스에 전달한다")
    void forwardsSystemRole() throws Exception {
        RevenueCommandService service = mock(RevenueCommandService.class);
        mvc(service).perform(patch(url).header("X-User-Role", "SYSTEM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferStatus\":\"TRANSFERRED\"}"))
                .andExpect(status().isOk());
        verify(service).updateTransferStatus(revenueId, "SYSTEM",
                new RevenueTransferStatusRequest(RevenueTransferStatus.TRANSFERRED, null));
    }

    @Test
    @DisplayName("권한 헤더가 없으면 요청을 거부한다")
    void rejectsMissingRoleHeader() throws Exception {
        RevenueCommandService service = mock(RevenueCommandService.class);
        mvc(service).perform(patch(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferStatus\":\"TRANSFERRED\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "ISSUER", "ADMIN"})
    @DisplayName("일반 사용자 요청은 실제 서비스에서 403으로 차단한다")
    void rejectsNonSystemRoles(String role) throws Exception {
        RevenueRepository repository = mock(RevenueRepository.class);
        RevenueQueryRepository queryRepository = mock(RevenueQueryRepository.class);
        RevenueCommandService service = new RevenueCommandService(repository, queryRepository,
                mock(AssetQueryRepository.class));
        mvc(service).perform(patch(url).header("X-User-Role", role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferStatus\":\"TRANSFERRED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, queryRepository);
    }
}
