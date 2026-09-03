package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.InternalRevenueListResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueListResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.entity.RevenueType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.RevenueQueryRepository;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RevenueQueryServiceTest {

    @Mock
    private RevenueQueryRepository revenueQueryRepository;

    @Mock
    private AssetQueryRepository assetQueryRepository;

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

    @Test
    @DisplayName("READY 상태 수익 목록을 커서 방식으로 조회한다")
    void getsReadyRevenuesWithCursorPagination() {
        UUID firstRevenueId = UUID.randomUUID();
        UUID secondRevenueId = UUID.randomUUID();
        UUID extraRevenueId = UUID.randomUUID();

        when(revenueQueryRepository.findReadyRevenues(null, 3))
                .thenReturn(List.of(
                        revenue(firstRevenueId, "RENT-1"),
                        revenue(secondRevenueId, "RENT-2"),
                        revenue(extraRevenueId, "RENT-3")
                ));

        InternalRevenueListResponse response =
                revenueQueryService.getReadyRevenues(null, 2);

        assertEquals(2, response.revenues().size());
        assertEquals(firstRevenueId, response.revenues().get(0).revenueId());
        assertEquals(secondRevenueId, response.revenues().get(1).revenueId());
        assertEquals(secondRevenueId, response.nextCursor());
        assertTrue(response.hasNext());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISSUER", "ADMIN"})
    @DisplayName("운용자는 본인 자산을, 관리자는 다른 자산도 커서로 조회한다")
    void getsAssetRevenues(String role) {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ownerId = "ISSUER".equals(role) ? userId : UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        Revenue first = revenue(UUID.randomUUID(), "RENT-1");
        Revenue second = revenue(UUID.randomUUID(), "RENT-2");
        Revenue extra = revenue(UUID.randomUUID(), "RENT-3");
        // 조회 대상 자산과 테스트 데이터의 자산을 일치시킴
        for (Revenue item : List.of(first, second, extra)) {
            ReflectionTestUtils.setField(item, "assetId", assetId);
        }
        when(assetQueryRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(ownerId)));
        when(revenueQueryRepository.findByAssetId(assetId, cursor, 3))
                .thenReturn(List.of(first, second, extra));

        RevenueListResponse response =
                revenueQueryService.getRevenues(assetId, userId, role, cursor, 2);

        assertEquals(List.of(RevenueDetailResponse.from(first), RevenueDetailResponse.from(second)),
                response.revenues());
        assertTrue(response.hasNext());
        assertEquals(second.getId(), response.nextCursor());
        verify(revenueQueryRepository).findByAssetId(assetId, cursor, 3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("조회 결과가 요청 크기 이하면 다음 페이지가 없다")
    void returnsLastOrEmptyPage(int count) {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<Revenue> items = List.of(
                revenue(UUID.randomUUID(), "RENT-1"),
                revenue(UUID.randomUUID(), "RENT-2")
        ).subList(0, count);
        when(assetQueryRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(userId)));
        when(revenueQueryRepository.findByAssetId(assetId, null, 3)).thenReturn(items);

        RevenueListResponse response =
                revenueQueryService.getRevenues(assetId, userId, "ISSUER", null, 2);

        assertEquals(count, response.revenues().size());
        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "SYSTEM"})
    @DisplayName("목록 조회 권한이 없으면 저장소를 호출하지 않는다")
    void rejectsUnauthorizedRole(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueQueryService.getRevenues(
                        UUID.randomUUID(), UUID.randomUUID(), role, null, 20));

        assertEquals(AssetErrorCode.REVENUE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetQueryRepository, revenueQueryRepository);
    }

    @Test
    @DisplayName("다른 자산운용자의 수익 목록은 조회할 수 없다")
    void rejectsOtherOwner() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset(UUID.randomUUID())));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueQueryService.getRevenues(
                        assetId, UUID.randomUUID(), "ISSUER", null, 20));

        assertEquals(AssetErrorCode.REVENUE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(revenueQueryRepository);
    }

    @Test
    @DisplayName("자산이 없으면 수익 목록을 조회하지 않는다")
    void rejectsMissingAsset() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueQueryService.getRevenues(
                        assetId, UUID.randomUUID(), "ADMIN", null, 20));

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(revenueQueryRepository);
    }

    private Asset asset(UUID ownerId) {
        return new Asset(ownerId, "테스트 자산", AssetType.REAL_ESTATE, "테스트 부동산",
                100_000_000L, BigDecimal.valueOf(5), Map.of(), 10_000L, 10_000L);
    }

    private Revenue revenue(UUID revenueId, String sourceReferenceId) {
        Revenue revenue = new Revenue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RevenueSourceType.PROPERTY_MANAGER,
                sourceReferenceId,
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
