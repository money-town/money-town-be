package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotResponse;
import com.moneykk.moneytown.asset.dto.response.MyAssetHoldingResponse;
import com.moneykk.moneytown.asset.dto.response.MyHoldingItemResponse;
import com.moneykk.moneytown.asset.dto.response.MyHoldingListResponse;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 컨트롤러의 권한 분기 검증 */
@ExtendWith(MockitoExtension.class)
class HoldingControllerTest {

    @Mock
    private HoldingQueryService holdingQueryService;

    @InjectMocks
    private HoldingController holdingController;

    @Test
    @DisplayName("내 보유지분 조회 요청을 서비스에 전달한다")
    void getsMyHolding() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String role = "INVESTOR";
        MyAssetHoldingResponse response = new MyAssetHoldingResponse(
                UUID.randomUUID(), assetId, 25L, Instant.now()
        );
        when(holdingQueryService.getMyHolding(assetId, userId, role))
                .thenReturn(response);
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(holdingController)
                .build();

        mvc.perform(get("/api/v1/assets/{assetId}/holdings/me", assetId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role))
                .andExpect(status().isOk());

        verify(holdingQueryService).getMyHolding(assetId, userId, role);
    }

    @Test
    @DisplayName("내 전체 보유지분 목록 조회 요청을 서비스에 전달한다")
    void getsMyHoldings() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MyHoldingItemResponse item = new MyHoldingItemResponse(
                holdingId, assetId, "강남 오피스 A동",
                AssetType.REAL_ESTATE, 100L,
                Instant.parse("2026-09-01T03:00:00Z"));
        when(holdingQueryService.getMyHoldings(
                userId, "INVESTOR", null, 20, Sort.Direction.DESC))
                .thenReturn(new MyHoldingListResponse(
                        List.of(item), null, false));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(holdingController)
                .build();

        mvc.perform(get("/api/v1/assets/holdings/me")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "INVESTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].holdingId")
                        .value(holdingId.toString()))
                .andExpect(jsonPath("$.data.items[0].assetId")
                        .value(assetId.toString()))
                .andExpect(jsonPath("$.data.items[0].assetName")
                        .value("강남 오피스 A동"))
                .andExpect(jsonPath("$.data.items[0].assetType")
                        .value("REAL_ESTATE"))
                .andExpect(jsonPath("$.data.items[0].quantity")
                        .value(100))
                .andExpect(jsonPath("$.data.hasNext")
                        .value(false));

        verify(holdingQueryService).getMyHoldings(
                userId, "INVESTOR", null, 20, Sort.Direction.DESC);
    }

    @ParameterizedTest
    @EnumSource(Sort.Direction.class)
    @DisplayName("SYSTEM은 기준일과 커서를 전달해 지분 스냅샷을 조회한다")
    void systemCanGetSnapshot(Sort.Direction direction) {
        UUID assetId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        LocalDate asOf = LocalDate.of(2026, 8, 31);
        HoldingSnapshotResponse snapshot = new HoldingSnapshotResponse(
                assetId, asOf,
                List.of(new HoldingSnapshotItemResponse(holdingId, UUID.randomUUID(), 10L)),
                holdingId, true
        );
        when(holdingQueryService.getSnapshot(assetId, asOf, cursor, 1, direction)).thenReturn(snapshot);

        ApiResponse<HoldingSnapshotResponse> response =
                holdingController.getSnapshot(assetId, "SYSTEM", asOf, cursor, 1, direction);

        assertTrue(response.success());
        assertSame(snapshot, response.data());
        verify(holdingQueryService).getSnapshot(assetId, asOf, cursor, 1, direction);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "ISSUER", "INVESTOR", "system", " SYSTEM "})
    @DisplayName("SYSTEM이 아니면 조회 서비스를 호출하지 않는다")
    void rejectsNonSystemRole(String role) {
        // 권한 검사 실패 시 조회 자체가 실행되면 안 됨
        BusinessException exception = assertThrows(BusinessException.class,
                () -> holdingController.getSnapshot(
                        UUID.randomUUID(), role, LocalDate.of(2026, 8, 31), null, 100, Sort.Direction.DESC));

        assertEquals(AssetErrorCode.HOLDING_SNAPSHOT_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(holdingQueryService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ASC", "DESC"})
    @DisplayName("HTTP 정렬 파라미터를 전달하고 생략 시 DESC를 사용한다")
    void bindsDirectionWithDescendingDefault(String directionParameter) throws Exception {
        UUID assetId = UUID.randomUUID();
        LocalDate asOf = LocalDate.of(2026, 8, 31);
        Sort.Direction expected = directionParameter == null
                ? Sort.Direction.DESC : Sort.Direction.valueOf(directionParameter);
        when(holdingQueryService.getSnapshot(assetId, asOf, null, 100, expected))
                .thenReturn(new HoldingSnapshotResponse(assetId, asOf, List.of(), null, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(holdingController).build();
        MockHttpServletRequestBuilder request = get("/api/v1/assets/{assetId}/holdings", assetId)
                .header("X-User-Role", "SYSTEM")
                .param("asOf", asOf.toString());
        if (directionParameter != null) {
            request.param("direction", directionParameter);
        }

        mvc.perform(request).andExpect(status().isOk());

        verify(holdingQueryService).getSnapshot(assetId, asOf, null, 100, expected);
    }
}
