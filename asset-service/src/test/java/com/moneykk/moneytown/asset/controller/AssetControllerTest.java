package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.AssetUpdateRequest;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.asset.service.AssetCommandService;
import com.moneykk.moneytown.asset.service.AssetQueryService;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssetControllerTest {

    private final UUID assetId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String url = "/api/v1/assets/" + assetId;
    private AssetCommandService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AssetCommandService.class);
        // 서버와 DB 없이 요청 변환·검증·응답만 확인
        mvc = MockMvcBuilders.standaloneSetup(new AssetController(service, mock(AssetQueryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PATCH 요청 필드와 사용자 헤더를 서비스에 전달한다")
    void updatesAsset() throws Exception {
        mvc.perform(request("""
                        {
                          "name": "수정한 자산",
                          "ownerName": "예시자산",
                          "detail": {"address": "서울"},
                          "totalShareQuantity": 1000000
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("자산 정보가 수정되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());

        ArgumentCaptor<AssetUpdateRequest> captor = ArgumentCaptor.forClass(AssetUpdateRequest.class);
        verify(service).updateAsset(eq(assetId), eq(userId), eq("ISSUER"), captor.capture());
        AssetUpdateRequest update = captor.getValue();
        assertEquals("수정한 자산", update.name());
        assertEquals("예시자산", update.ownerName());
        assertEquals("서울", update.detail().get("address"));
        assertEquals(Long.valueOf(1_000_000L), update.totalShareQuantity());
    }

    @Test
    @DisplayName("자산명만 전달하는 부분 수정 요청을 허용한다")
    void acceptsPartialRequest() throws Exception {
        mvc.perform(request("{\"name\":\"새 자산명\"}"))
                .andExpect(status().isOk());
        verify(service).updateAsset(assetId, userId, "ISSUER",
                new AssetUpdateRequest("새 자산명", null, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \"}", "{\"name\":\"가\"}",
            "{\"description\":\" \"}", "{\"ownerName\":\" \"}",
            "{\"totalShareQuantity\":0}", "{\"totalShareQuantity\":-1}",
            "{\"detail\":[]}", "{"
    })
    @DisplayName("잘못된 요청은 400으로 반환하고 서비스를 호출하지 않는다")
    void rejectsInvalidRequest(String body) throws Exception {
        mvc.perform(request(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-User-Id", "X-User-Role"})
    @DisplayName("필수 사용자 헤더가 없으면 서비스를 호출하지 않는다")
    void rejectsMissingHeader(String missingHeader) throws Exception {
        MockHttpServletRequestBuilder request = patch(url)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 자산명\"}");
        if (!"X-User-Id".equals(missingHeader)) request.header("X-User-Id", userId);
        if (!"X-User-Role".equals(missingHeader)) request.header("X-User-Role", "ISSUER");

        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = AssetErrorCode.class, names = {
            "ASSET_UPDATE_ACCESS_DENIED", "ASSET_UPDATE_NOT_ALLOWED",
            "ASSET_NOT_FOUND", "INVALID_ASSET_SHARE_PRICE", "APPRAISAL_AMOUNT_UPDATE_NOT_ALLOWED"
    })
    @DisplayName("서비스 오류를 정해진 HTTP 상태와 오류 코드로 반환한다")
    void returnsBusinessError(AssetErrorCode errorCode) throws Exception {
        doThrow(new BusinessException(errorCode)).when(service)
                .updateAsset(eq(assetId), eq(userId), eq("ISSUER"), any(AssetUpdateRequest.class));

        mvc.perform(request("{\"name\":\"새 자산명\"}"))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"120000000", "100000000", "null"})
    @DisplayName("평가금액이 포함된 HTTP 요청은 실제 서비스에서 400으로 거부한다")
    void rejectsAppraisalAmountUpdate(String amount) throws Exception {
        AssetQueryRepository queryRepository = mock(AssetQueryRepository.class);
        Asset asset = new Asset(userId, "기존 자산", AssetType.REAL_ESTATE, "기존 설명",
                100_000_000L, BigDecimal.ZERO, Map.of("appraisalAmount", 100_000_000L), 10_000L);
        when(queryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));
        AssetCommandService realService = new AssetCommandService(mock(AssetRepository.class), queryRepository);
        MockMvc realMvc = MockMvcBuilders.standaloneSetup(
                        new AssetController(realService, mock(AssetQueryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        realMvc.perform(request("{\"detail\":{\"appraisalAmount\":" + amount
                        + "},\"totalShareQuantity\":30000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSET_400_14"))
                .andExpect(jsonPath("$.message").value("자산 평가금액은 수정할 수 없습니다."));
        assertEquals(100_000_000L, asset.getValuationAmount());
        assertEquals(10_000L, asset.getTotalShareQuantity());
        assertEquals(10_000L, asset.getUnitPrice());
    }

    @Test
    @DisplayName("자산 상태 변경 요청을 서비스에 전달한다")
    void changesAssetStatus() throws Exception {
        mvc.perform(patch(url + "/status")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REJECTED",
                                  "rejectionReason": "서류 보완 필요"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("자산 상태가 변경되었습니다."));

        verify(service).changeAssetStatus(
                assetId, userId, "ADMIN", AssetStatus.REJECTED, "서류 보완 필요");
    }

    @Test
    @DisplayName("변경할 상태가 없으면 400을 반환한다")
    void rejectsMissingAssetStatus() throws Exception {
        mvc.perform(patch(url + "/status")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "ISSUER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("자산 삭제 요청을 서비스에 전달한다")
    void deletesAsset() throws Exception {
        mvc.perform(delete(url)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "ISSUER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("자산이 삭제되었습니다."));

        verify(service).deleteAsset(assetId, userId, "ISSUER");
    }

    private MockHttpServletRequestBuilder request(String body) {
        return patch(url).header("X-User-Id", userId).header("X-User-Role", "ISSUER")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
