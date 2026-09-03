package com.moneykk.moneytown.asset.dto.request;

import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RevenueCreateRequestTest {

    @ParameterizedTest
    @ValueSource(strings = {"100.005", "0.001", "100000000000000000", "1E+17"})
    @DisplayName("DB 범위를 넘는 세 금액 필드를 요청과 엔티티에서 거부한다")
    void rejectsAmountsOutsideDatabaseRange(String value) {
        RevenueCreateRequest request = request(new BigDecimal(value));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Set<String> fields = factory.getValidator().validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("grossAmount", "expenseAmount", "feeAmount"), fields);
        }

        // 각 금액 필드를 따로 바꿔 엔티티 검증도 확인
        BigDecimal[] amounts = {BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO};
        for (int index = 0; index < amounts.length; index++) {
            BigDecimal original = amounts[index];
            amounts[index] = new BigDecimal(value);
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> revenue(amounts[0], amounts[1], amounts[2]));
            assertEquals(AssetErrorCode.INVALID_REVENUE_AMOUNT, exception.getErrorCode());
            amounts[index] = original;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "100", "100.25", "99999999999999999.99", "1E+16"})
    @DisplayName("허용 범위의 금액은 반올림 없이 유지한다")
    void acceptsAmountsWithinDatabaseRange(String value) {
        BigDecimal amount = new BigDecimal(value);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request(amount)).isEmpty());
        }
        Revenue revenue = revenue(amount, amount, amount);
        assertEquals(amount, revenue.getGrossAmount());
        assertEquals(amount, revenue.getExpenseAmount());
        assertEquals(amount, revenue.getFeeAmount());
    }

    private RevenueCreateRequest request(BigDecimal amount) {
        return new RevenueCreateRequest(RevenueSourceType.PROPERTY_MANAGER, "RENT-1",
                RevenueType.RENTAL_INCOME, amount, amount, amount, "KRW",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), Map.of());
    }

    private Revenue revenue(BigDecimal gross, BigDecimal expense, BigDecimal fee) {
        return new Revenue(UUID.randomUUID(), UUID.randomUUID(), RevenueSourceType.PROPERTY_MANAGER,
                "RENT-1", RevenueType.RENTAL_INCOME, gross, expense, fee, "KRW",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), Map.of());
    }
}
