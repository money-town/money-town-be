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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RevenueControllerTest {
    private final UUID revenueId = UUID.randomUUID();
    private final String url = "/api/v1/assets/revenues/" + revenueId + "/transfer-status";

    private MockMvc mvc(RevenueCommandService service) {
        return mvc(mock(RevenueQueryService.class), service);
    }

    private MockMvc mvc(
            RevenueQueryService queryService,
            RevenueCommandService commandService
    ) {
        return MockMvcBuilders.standaloneSetup(
                        new RevenueController(queryService, commandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("SYSTEM은 수익 단건을 조회할 수 있다")
    void systemCanGetRevenue() throws Exception {
        UUID assetId = UUID.randomUUID();
        RevenueQueryService queryService = mock(RevenueQueryService.class);

        mvc(queryService, mock(RevenueCommandService.class))
                .perform(get(
                                "/api/v1/assets/{assetId}/revenues/{revenueId}",
                                assetId,
                                revenueId
                        )
                        .header("X-User-Role", "SYSTEM"))
                .andExpect(status().isOk());

        verify(queryService).getRevenue(assetId, revenueId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "ISSUER", "ADMIN"})
    @DisplayName("SYSTEM이 아닌 사용자는 수익 단건을 조회할 수 없다")
    void rejectsRevenueDetailForNonSystemRole(String role) throws Exception {
        UUID assetId = UUID.randomUUID();
        RevenueQueryService queryService = mock(RevenueQueryService.class);

        mvc(queryService, mock(RevenueCommandService.class))
                .perform(get(
                                "/api/v1/assets/{assetId}/revenues/{revenueId}",
                                assetId,
                                revenueId
                        )
                        .header("X-User-Role", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ASSET_403_13"))
                .andExpect(jsonPath("$.message")
                        .value("수익 단건 조회는 SYSTEM 권한으로만 호출할 수 있습니다."));

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("권한 헤더가 없으면 수익 단건 조회를 거부한다")
    void rejectsRevenueDetailWithoutRoleHeader() throws Exception {
        UUID assetId = UUID.randomUUID();
        RevenueQueryService queryService = mock(RevenueQueryService.class);

        mvc(queryService, mock(RevenueCommandService.class))
                .perform(get(
                        "/api/v1/assets/{assetId}/revenues/{revenueId}",
                        assetId,
                        revenueId
                ))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
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
