package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.RevenueCreateRequest;
import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueTransferStatusResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.entity.RevenueType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.RevenueRepository;
import com.moneykk.moneytown.asset.repository.RevenueQueryRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RevenueCommandServiceTest {

    @Mock
    private RevenueRepository revenueRepository;

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @Mock
    private RevenueQueryRepository revenueQueryRepository;

    @InjectMocks
    private RevenueCommandService revenueCommandService;

    @Test
    @DisplayName("수익 전달 완료 상태로 변경한다")
    void marksRevenueAsTransferred() {
        UUID revenueId = UUID.randomUUID();
        Revenue revenue = revenue(revenueId);
        when(revenueQueryRepository.findByIdForUpdate(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(
                        revenueId,
                        "SYSTEM",
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
        when(revenueQueryRepository.findByIdForUpdate(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(
                        revenueId,
                        "SYSTEM",
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
        when(revenueQueryRepository.findByIdForUpdate(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(
                        revenueId,
                        "SYSTEM",
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
        when(revenueQueryRepository.findByIdForUpdate(revenueId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> revenueCommandService.updateTransferStatus(
                        revenueId,
                        "SYSTEM",
                        new RevenueTransferStatusRequest(
                                RevenueTransferStatus.TRANSFERRED,
                                null
                        )
                )
        );

        assertEquals(AssetErrorCode.REVENUE_NOT_FOUND, exception.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISSUER", "ADMIN", "SYSTEM"})
    @DisplayName("허용된 역할은 수익을 READY 상태로 등록한다")
    void createsRevenue(String role) {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        // 관리자와 시스템은 다른 소유자의 자산에도 등록 가능
        UUID ownerId = "ISSUER".equals(role) ? userId : UUID.randomUUID();
        RevenueCreateRequest request = createRequest();
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(revenueRepository.save(any(Revenue.class))).thenAnswer(invocation -> {
            Revenue saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", revenueId);
            assertEquals(userId, saved.getUserId());
            assertEquals(request.rawPayload(), saved.getRawPayload());
            return saved;
        });

        RevenueDetailResponse response =
                revenueCommandService.createRevenue(assetId, userId, role, request);

        assertEquals(revenueId, response.revenueId());
        assertEquals(assetId, response.assetId());
        assertEquals(request.sourceType(), response.sourceType());
        assertEquals(request.sourceReferenceId(), response.sourceReferenceId());
        assertEquals(request.revenueType(), response.revenueType());
        assertEquals(request.grossAmount(), response.grossAmount());
        assertEquals(request.expenseAmount(), response.expenseAmount());
        assertEquals(request.feeAmount(), response.feeAmount());
        assertEquals(request.currency(), response.currency());
        assertEquals(request.periodStart(), response.periodStart());
        assertEquals(request.periodEnd(), response.periodEnd());
        assertEquals(RevenueTransferStatus.READY, response.transferStatus());
        verify(revenueRepository).existsByAssetIdAndSourceTypeAndSourceReferenceId(
                assetId, request.sourceType(), request.sourceReferenceId());
        verify(revenueRepository).save(any(Revenue.class));
    }

    @Test
    @DisplayName("동일 출처 수익이 있으면 중복 등록을 거부한다")
    void rejectsDuplicateRevenue() {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RevenueCreateRequest request = createRequest();
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(userId)));
        when(revenueRepository.existsByAssetIdAndSourceTypeAndSourceReferenceId(
                assetId, request.sourceType(), request.sourceReferenceId())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueCommandService.createRevenue(assetId, userId, "ISSUER", request));

        assertEquals(AssetErrorCode.DUPLICATE_REVENUE, exception.getErrorCode());
        verify(revenueRepository, never()).save(any(Revenue.class));
    }

    @Test
    @DisplayName("투자자는 수익을 등록할 수 없다")
    void rejectsInvestor() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueCommandService.createRevenue(
                        UUID.randomUUID(), UUID.randomUUID(), "INVESTOR", createRequest()));

        assertEquals(AssetErrorCode.REVENUE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetQueryRepository, revenueRepository);
    }

    @Test
    @DisplayName("자산운용자는 다른 소유자의 자산에 수익을 등록할 수 없다")
    void rejectsOtherIssuer() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(UUID.randomUUID())));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueCommandService.createRevenue(
                        assetId, UUID.randomUUID(), "ISSUER", createRequest()));

        assertEquals(AssetErrorCode.REVENUE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(revenueRepository);
    }

    @Test
    @DisplayName("등록할 자산이 없으면 ASSET_NOT_FOUND 예외를 반환한다")
    void rejectsMissingAsset() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueCommandService.createRevenue(
                        assetId, UUID.randomUUID(), "SYSTEM", createRequest()));

        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(revenueRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "ISSUER", "ADMIN", ""})
    @DisplayName("SYSTEM 외 역할은 수익 전달 상태를 변경할 수 없다")
    void rejectsTransferStatusForOtherRoles(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueCommandService.updateTransferStatus(UUID.randomUUID(), role,
                        new RevenueTransferStatusRequest(RevenueTransferStatus.TRANSFERRED, null)));
        assertEquals(AssetErrorCode.REVENUE_TRANSFER_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(revenueQueryRepository, revenueRepository);
    }

    @Test
    @DisplayName("완료 요청을 재전송해도 최초 완료 시간을 유지한다")
    void preservesCompletionTimeOnRetry() {
        UUID revenueId = UUID.randomUUID();
        Revenue revenue = revenue(revenueId);
        revenue.markTransferred();
        java.time.Instant firstTime = revenue.getTransferredAt();
        when(revenueQueryRepository.findByIdForUpdate(revenueId)).thenReturn(Optional.of(revenue));

        RevenueTransferStatusResponse response = revenueCommandService.updateTransferStatus(
                revenueId, "SYSTEM",
                new RevenueTransferStatusRequest(RevenueTransferStatus.TRANSFERRED, null));

        assertEquals(firstTime, response.transferredAt());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RevenueTransferStatus.class,
            names = {"READY", "FAILED"})
    @DisplayName("전달 완료된 수익을 대기나 실패 상태로 되돌릴 수 없다")
    void rejectsCompletedRevenueRegression(RevenueTransferStatus target) {
        UUID revenueId = UUID.randomUUID();
        Revenue revenue = revenue(revenueId);
        revenue.markTransferred();
        java.time.Instant firstTime = revenue.getTransferredAt();
        when(revenueQueryRepository.findByIdForUpdate(revenueId)).thenReturn(Optional.of(revenue));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> revenueCommandService.updateTransferStatus(revenueId, "SYSTEM",
                        new RevenueTransferStatusRequest(target, "늦게 도착한 실패 응답")));

        assertEquals(AssetErrorCode.INVALID_REVENUE_TRANSFER_STATUS, exception.getErrorCode());
        assertEquals(RevenueTransferStatus.TRANSFERRED, revenue.getTransferStatus());
        assertEquals(firstTime, revenue.getTransferredAt());
    }

    @Test
    @DisplayName("실패 요청이 반복되어도 최초 실패 사유를 유지한다")
    void preservesOriginalFailure() {
        Revenue revenue = revenue(UUID.randomUUID());
        revenue.markFailed("최초 실패");
        revenue.markFailed("재전송 실패");
        assertEquals("최초 실패", revenue.getFailureReason());
    }

    private Asset asset(UUID ownerId) {
        return new Asset(ownerId, "테스트 자산", AssetType.REAL_ESTATE, "테스트 부동산",
                100_000_000L, BigDecimal.valueOf(5), Map.of(), 10_000L);
    }

    private RevenueCreateRequest createRequest() {
        return new RevenueCreateRequest(
                RevenueSourceType.PROPERTY_MANAGER, "RENT-2026-09", RevenueType.RENTAL_INCOME,
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(100_000), BigDecimal.valueOf(50_000),
                "KRW", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                Map.of("source", "임대관리 시스템"));
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
