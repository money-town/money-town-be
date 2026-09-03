package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.OfferingCompletionRequest;
import com.moneykk.moneytown.asset.dto.response.OwnerBurdenQuoteResponse;
import com.moneykk.moneytown.asset.entity.*;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerBurdenServiceTest {
    private final AssetQueryRepository repository = mock(AssetQueryRepository.class);
    private final OwnerBurdenService service = new OwnerBurdenService(repository);
    private final UUID assetId = UUID.randomUUID();
    private final OfferingCompletionRequest request = new OfferingCompletionRequest(
            UUID.randomUUID(), Instant.parse("2025-01-01T00:00:00Z"));

    @Test
    @DisplayName("공모 완료 처리 시 행 잠금 조회를 사용하고 원금과 이자를 조회한다")
    void completesAndQuotes() {
        Asset asset = asset();
        when(repository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));
        when(repository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        service.recordOfferingCompletion(assetId, "SYSTEM", request);
        service.recordOfferingCompletion(assetId, "SYSTEM", request);
        OwnerBurdenQuoteResponse quote = service.getQuote(assetId, "SYSTEM", LocalDate.of(2026, 1, 1));
        assertEquals(1000, quote.principalAmount());
        assertEquals(new BigDecimal("100"), quote.interestAmount());
        assertEquals(new BigDecimal("1100"), quote.totalAmount());
        assertEquals(request.completedAt(), quote.offeringCompletedAt());
        verify(repository, times(2)).findActiveByIdForUpdate(assetId);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ISSUER", "ADMIN", "INVESTOR"})
    @DisplayName("SYSTEM이 아니면 완료 통지와 견적 조회를 차단한다")
    void rejectsOtherRoles(String role) {
        assertThrows(BusinessException.class, () -> service.recordOfferingCompletion(assetId, role, request));
        assertThrows(BusinessException.class, () -> service.getQuote(assetId, role, LocalDate.now()));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("미래 완료 시각은 저장하지 않는다")
    void rejectsFutureCompletion() {
        assertThrows(BusinessException.class, () -> service.recordOfferingCompletion(assetId, "SYSTEM",
                new OfferingCompletionRequest(UUID.randomUUID(), Instant.now().plusSeconds(3600))));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("공모 완료 전이나 완료일 이전 기준일의 견적을 차단한다")
    void rejectsUnavailableQuote() {
        Asset asset = asset();
        when(repository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getQuote(assetId, "SYSTEM", LocalDate.of(2026, 1, 1)));
        assertEquals(AssetErrorCode.OFFERING_NOT_COMPLETED, exception.getErrorCode());
        asset.recordOfferingCompletion(request.offeringId(), request.completedAt());
        assertThrows(BusinessException.class,
                () -> service.getQuote(assetId, "SYSTEM", LocalDate.of(2024, 12, 31)));
    }

    private Asset asset() {
        Asset asset = new Asset(UUID.randomUUID(), "자산", AssetType.REAL_ESTATE, "설명",
                101000, BigDecimal.ZERO, Map.of(), 10000);
        asset.selectOwnerBurdenPaymentMethod(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        ReflectionTestUtils.setField(asset, "id", assetId);
        ReflectionTestUtils.setField(asset, "status", AssetStatus.APPROVED);
        return asset;
    }
}
