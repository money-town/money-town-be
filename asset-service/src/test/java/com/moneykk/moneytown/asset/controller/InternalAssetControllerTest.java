package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.InternalAssetSummaryResponse;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.service.AssetCommandService;
import com.moneykk.moneytown.asset.service.AssetQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalAssetControllerTest {

    @Mock
    private AssetQueryService assetQueryService;

    @Mock
    private AssetCommandService assetCommandService;

    @InjectMocks
    private InternalAssetController controller;

    @Test
    @DisplayName("인증 헤더 없이 여러 자산을 한 번에 조회한다")
    void getsInternalAssets() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        List<UUID> assetIds = List.of(firstId, secondId);
        InternalAssetSummaryResponse response =
                new InternalAssetSummaryResponse(
                        firstId,
                        AssetType.REAL_ESTATE,
                        "강남 오피스",
                        AssetStatus.APPROVED,
                        BigDecimal.valueOf(5.5),
                        1_000_000_000L,
                        "강남 오피스 자산",
                        Map.of("address", "서울")
                );
        when(assetQueryService.getInternalAssets(assetIds))
                .thenReturn(List.of(response));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        mvc.perform(get("/api/v1/internal/assets")
                        .param("assetIds", firstId + "," + secondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assetId")
                        .value(firstId.toString()))
                .andExpect(jsonPath("$.data[0].assetName")
                        .value("강남 오피스"))
                .andExpect(jsonPath("$.message")
                        .value("내부 자산 목록 조회가 완료되었습니다."));

        verify(assetQueryService).getInternalAssets(assetIds);
    }

    @Test
    @DisplayName("정산 서비스의 자산 종료 완료 요청을 전달한다")
    void completesAssetTermination() throws Exception {
        UUID assetId = UUID.randomUUID();
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        mvc.perform(patch(
                        "/api/v1/internal/assets/{assetId}/termination-completion",
                        assetId
                ).header("X-User-Role", "SYSTEM"))
                .andExpect(status().isOk());

        verify(assetCommandService)
                .completeAssetTermination(assetId, "SYSTEM");
    }
}
