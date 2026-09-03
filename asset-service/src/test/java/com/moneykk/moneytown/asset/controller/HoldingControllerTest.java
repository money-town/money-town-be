package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotResponse;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 컨트롤러의 권한 분기 검증 */
@ExtendWith(MockitoExtension.class)
class HoldingControllerTest {

    @Mock
    private HoldingQueryService holdingQueryService;

    @InjectMocks
    private HoldingController holdingController;

    @Test
    @DisplayName("SYSTEM은 기준일과 커서를 전달해 지분 스냅샷을 조회한다")
    void systemCanGetSnapshot() {
        UUID assetId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();
        LocalDate asOf = LocalDate.of(2026, 8, 31);
        HoldingSnapshotResponse snapshot = new HoldingSnapshotResponse(
                assetId, asOf,
                List.of(new HoldingSnapshotItemResponse(holdingId, UUID.randomUUID(), 10L)),
                holdingId, true
        );
        when(holdingQueryService.getSnapshot(assetId, asOf, cursor, 1)).thenReturn(snapshot);

        ApiResponse<HoldingSnapshotResponse> response =
                holdingController.getSnapshot(assetId, "SYSTEM", asOf, cursor, 1);

        assertTrue(response.success());
        assertSame(snapshot, response.data());
        verify(holdingQueryService).getSnapshot(assetId, asOf, cursor, 1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "ISSUER", "INVESTOR", "system", " SYSTEM "})
    @DisplayName("SYSTEM이 아니면 조회 서비스를 호출하지 않는다")
    void rejectsNonSystemRole(String role) {
        // 권한 검사 실패 시 조회 자체가 실행되면 안 됨
        BusinessException exception = assertThrows(BusinessException.class,
                () -> holdingController.getSnapshot(
                        UUID.randomUUID(), role, LocalDate.of(2026, 8, 31), null, 100));

        assertEquals(AssetErrorCode.HOLDING_SNAPSHOT_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(holdingQueryService);
    }
}
