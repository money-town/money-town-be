package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.entity.RevenueType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.RevenueQueryRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueQueryServiceTest {

    @Mock
    private RevenueQueryRepository revenueQueryRepository;

    @InjectMocks
    private RevenueQueryService revenueQueryService;

    @Test
    @DisplayName("자산에 등록된 수익을 조회한다")
    void getsRevenue() {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();

        Revenue revenue = new Revenue(
                assetId,
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

        when(revenueQueryRepository.findByAssetIdAndRevenueId(assetId, revenueId))
                .thenReturn(Optional.of(revenue));

        RevenueDetailResponse response =
                revenueQueryService.getRevenue(assetId, revenueId);

        assertEquals(revenueId, response.revenueId());
        assertEquals(assetId, response.assetId());
        assertEquals(BigDecimal.valueOf(1_000_000), response.grossAmount());
        assertEquals(RevenueTransferStatus.READY, response.transferStatus());
    }

    @Test
    @DisplayName("수익이 없으면 REVENUE_NOT_FOUND 예외를 반환한다")
    void throwsWhenRevenueDoesNotExist() {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();

        when(revenueQueryRepository.findByAssetIdAndRevenueId(assetId, revenueId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> revenueQueryService.getRevenue(assetId, revenueId)
        );

        assertEquals(AssetErrorCode.REVENUE_NOT_FOUND, exception.getErrorCode());
    }
}
