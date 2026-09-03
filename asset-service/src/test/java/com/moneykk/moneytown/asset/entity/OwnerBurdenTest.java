package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnerBurdenTest {
    private final Instant completedAt = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    @DisplayName("자산 등록만으로는 이자가 발생하지 않는다")
    void noInterestBeforeCompletion() {
        Asset asset = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        assertEquals(BigDecimal.ZERO, asset.calculateOwnerBurdenInterest(LocalDate.of(2026, 1, 1)));
        assertNull(asset.getOfferingCompletedAt());
        assertNull(asset.getOwnerBurdenPrincipal());
    }

    @ParameterizedTest
    @CsvSource({"2024-12-31, 0", "2025-01-01, 0", "2025-01-04, 0",
            "2025-01-05, 1", "2026-01-01, 100", "2027-01-01, 200"})
    @DisplayName("공모 완료일부터 365일 기준 단리 이자를 누적 후 절사한다")
    void accruesSimpleInterest(LocalDate asOf, long expected) {
        Asset asset = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        asset.recordOfferingCompletion(UUID.randomUUID(), completedAt);
        assertEquals(1000L, asset.getOwnerBurdenPrincipal().longValue());
        assertEquals(BigDecimal.valueOf(expected), asset.calculateOwnerBurdenInterest(asOf));
    }

    @Test
    @DisplayName("지갑 납부 방식에는 이자를 붙이지 않는다")
    void walletPaymentHasNoInterest() {
        Asset asset = asset(OwnerBurdenPaymentMethod.WALLET_PAYMENT);
        asset.recordOfferingCompletion(UUID.randomUUID(), completedAt);
        assertEquals(BigDecimal.ZERO, asset.calculateOwnerBurdenInterest(LocalDate.of(2027, 1, 1)));
    }

    @Test
    @DisplayName("한국 날짜 기준 완료일에는 이자가 붙지 않는다")
    void usesKoreanCompletionDate() {
        Asset asset = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        asset.recordOfferingCompletion(UUID.randomUUID(), Instant.parse("2025-01-01T23:00:00Z"));
        assertEquals(BigDecimal.ZERO, asset.calculateOwnerBurdenInterest(LocalDate.of(2025, 1, 2)));
        assertEquals(new BigDecimal("100"), asset.calculateOwnerBurdenInterest(LocalDate.of(2026, 1, 2)));
    }

    @Test
    @DisplayName("윤년에도 계약상 분모는 365일로 유지한다")
    void usesFixed365DayBasis() {
        Asset asset = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        asset.recordOfferingCompletion(UUID.randomUUID(), Instant.parse("2024-01-01T00:00:00Z"));
        assertEquals(new BigDecimal("100"), asset.calculateOwnerBurdenInterest(LocalDate.of(2025, 1, 1)));
    }

    @Test
    @DisplayName("완료 통지가 중복되어도 완료 시각과 원금을 유지한다")
    void completionIsIdempotent() {
        Asset asset = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        UUID offeringId = UUID.randomUUID();
        Instant preciseTime = Instant.parse("2025-01-01T00:00:00.123456789Z");
        asset.recordOfferingCompletion(offeringId, preciseTime);
        asset.recordOfferingCompletion(offeringId, preciseTime);
        assertEquals(Instant.parse("2025-01-01T00:00:00.123456Z"), asset.getOfferingCompletedAt());
        assertEquals(1000L, asset.getOwnerBurdenPrincipal().longValue());
        assertThrows(BusinessException.class,
                () -> asset.recordOfferingCompletion(offeringId, completedAt.plusSeconds(1)));
        assertThrows(BusinessException.class,
                () -> asset.recordOfferingCompletion(UUID.randomUUID(), preciseTime));
    }

    @Test
    @DisplayName("납부 방식 미선택 자산과 미승인 자산은 완료 처리하지 않는다")
    void rejectsUnconfiguredOrUnapprovedAsset() {
        Asset legacy = new Asset(UUID.randomUUID(), "자산", AssetType.REAL_ESTATE, "설명",
                101000, BigDecimal.ZERO, Map.of(), 10000);
        ReflectionTestUtils.setField(legacy, "status", AssetStatus.APPROVED);
        BusinessException missingMethod = assertThrows(BusinessException.class,
                () -> legacy.recordOfferingCompletion(UUID.randomUUID(), completedAt));
        assertEquals(AssetErrorCode.OWNER_BURDEN_METHOD_REQUIRED, missingMethod.getErrorCode());
        Asset draft = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        ReflectionTestUtils.setField(draft, "status", AssetStatus.DRAFT);
        assertThrows(BusinessException.class,
                () -> draft.recordOfferingCompletion(UUID.randomUUID(), completedAt));
    }

    @Test
    @DisplayName("확정한 납부 방식은 다시 선택할 수 없다")
    void cannotChangeMethod() {
        Asset asset = asset(OwnerBurdenPaymentMethod.SALE_DEDUCTION);
        assertThrows(BusinessException.class,
                () -> asset.selectOwnerBurdenPaymentMethod(OwnerBurdenPaymentMethod.WALLET_PAYMENT));
    }

    private Asset asset(OwnerBurdenPaymentMethod method) {
        Asset asset = new Asset(UUID.randomUUID(), "자산", AssetType.REAL_ESTATE, "설명",
                101000, BigDecimal.ZERO, Map.of(), 10000);
        asset.selectOwnerBurdenPaymentMethod(method);
        ReflectionTestUtils.setField(asset, "status", AssetStatus.APPROVED);
        return asset;
    }
}
