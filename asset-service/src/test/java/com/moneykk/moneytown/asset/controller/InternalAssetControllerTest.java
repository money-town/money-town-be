package com.moneykk.moneytown.asset.controller;

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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
