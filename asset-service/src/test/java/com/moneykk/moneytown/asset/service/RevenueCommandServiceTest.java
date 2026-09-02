package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.dto.response.RevenueTransferStatusResponse;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.entity.RevenueType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.RevenueRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueCommandServiceTest {

    @Mock
    private RevenueRepository revenueRepository;

    @InjectMocks
    private RevenueCommandService revenueCommandService;

    @Test
    @DisplayName("수익 전달 완료 상태로 변경한다")
    void marksRevenueAsTransferred() {
        UUID revenueId = UUID.randomUUID();
        Revenue revenue = revenue(revenueId);
        when(revenueRepository.findById(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(
                        revenueId,
                        new RevenueTransferStatusRequest(
                                RevenueTransferStatus.TRANSFERRED,
                                null
                        )
                );

        assertEquals(RevenueTransferStatus.TRANSFERRED, response.transferStatus());
        assertNotNull(response.transferredAt());
        assertNull(response.failureReason());
    }

    @Test
    @DisplayName("수익 전달 실패 상태와 실패 사유를 저장한다")
    void marksRevenueAsFailed() {
        UUID revenueId = UUID.randomUUID();
        Revenue revenue = revenue(revenueId);
        when(revenueRepository.findById(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(
                        revenueId,
                        new RevenueTransferStatusRequest(
                                RevenueTransferStatus.FAILED,
                                "정산 서비스 응답 시간 초과"
                        )
                );

        assertEquals(RevenueTransferStatus.FAILED, response.transferStatus());
        assertEquals("정산 서비스 응답 시간 초과", response.failureReason());
        assertNull(response.transferredAt());
    }

    @Test
    @DisplayName("전달에 실패한 수익을 재시도 대기 상태로 변경한다")
    void retriesFailedRevenue() {
        UUID revenueId = UUID.randomUUID();
        Revenue revenue = revenue(revenueId);
        revenue.markFailed("일시적인 장애");
        when(revenueRepository.findById(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(
                        revenueId,
                        new RevenueTransferStatusRequest(
                                RevenueTransferStatus.READY,
                                null
                        )
                );

        assertEquals(RevenueTransferStatus.READY, response.transferStatus());
        assertNull(response.transferredAt());
        assertNull(response.failureReason());
    }

    @Test
    @DisplayName("수익이 없으면 REVENUE_NOT_FOUND 예외를 반환한다")
    void throwsWhenRevenueDoesNotExist() {
        UUID revenueId = UUID.randomUUID();
        when(revenueRepository.findById(revenueId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> revenueCommandService.updateTransferStatus(
                        revenueId,
                        new RevenueTransferStatusRequest(
                                RevenueTransferStatus.TRANSFERRED,
                                null
                        )
                )
        );

        assertEquals(AssetErrorCode.REVENUE_NOT_FOUND, exception.getErrorCode());
    }

    private Revenue revenue(UUID revenueId) {
        Revenue revenue = new Revenue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RevenueSourceType.PROPERTY_MANAGER,
                "RENT-2026-09",
                RevenueType.RENTAL_INCOME,
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                "KRW",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                Map.of()
        );
        ReflectionTestUtils.setField(revenue, "id", revenueId);
        return revenue;
    }
}
