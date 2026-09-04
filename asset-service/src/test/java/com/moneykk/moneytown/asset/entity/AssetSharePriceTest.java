package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetSharePriceTest {

    @ParameterizedTest
    @CsvSource({"100000000, 10000, 10000, 0", "100, 100, 1, 0", "100, 3, 33, 1",
            "100000001, 10000, 10000, 1", "10009, 10, 1000, 9",
            "9223372036854775807, 1, 9223372036854775807, 0",
            "9223372036854775807, 2, 4611686018427387903, 1"})
    @DisplayName("단가는 소수점 이하를 버리고 차액을 별도 필드에 저장한다")
    void calculatesUnitPrice(long valuation, long quantity, long expectedPrice, long difference) {
        Asset asset = asset(valuation, quantity);
        assertEquals(expectedPrice, asset.getUnitPrice());
        assertEquals(difference, asset.getRoundingDifferenceAmount());
        assertEquals(valuation, asset.getUnitPrice() * asset.getTotalShareQuantity()
                + asset.getRoundingDifferenceAmount());
    }

    @ParameterizedTest
    @CsvSource({"100, 101", "0, 10", "-100, 10", "100, 0", "100, -10"})
    @DisplayName("평가 금액이나 수량이 양수가 아니거나 단가가 0원이면 거부한다")
    void rejectsInvalidPrice(long valuation, long quantity) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> asset(valuation, quantity));
        assertEquals(AssetErrorCode.INVALID_ASSET_SHARE_PRICE, exception.getErrorCode());
    }

    private Asset asset(long valuation, long quantity) {
        return new Asset(UUID.randomUUID(), "테스트 자산", AssetType.REAL_ESTATE, "자산 설명",
                valuation, BigDecimal.ZERO, Map.of(), quantity);
    }
}
