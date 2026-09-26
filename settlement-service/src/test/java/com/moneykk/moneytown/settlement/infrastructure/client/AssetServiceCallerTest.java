package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AssetServiceCallerTest {

    private static CallNotPermittedException circuitOpen() {
        return CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("asset-service"));
    }

    @Test
    @DisplayName("정상 호출 결과를 그대로 반환한다")
    void returnsSuccessfulResult() {
        String result = AssetServiceCaller.call(() -> "ok", SettlementErrorCode.ASSET_REVENUE_NOT_FOUND);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("서킷이 열려 호출이 거부되면 ASSET_SERVICE_UNAVAILABLE(503)로 변환하고 원인을 보존한다 — NOT_FOUND로 변환하면 안 된다")
    void mapsOpenCircuitToServiceUnavailable() {
        CallNotPermittedException cause = circuitOpen();

        assertThatThrownBy(() -> AssetServiceCaller.call(() -> {
            throw cause;
        }, SettlementErrorCode.ASSET_REVENUE_NOT_FOUND))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.ASSET_SERVICE_UNAVAILABLE);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(503);
                    assertThat(e.getCause()).isSameAs(cause);
                });
    }

    @Test
    @DisplayName("래퍼 예외 안에 CallNotPermittedException이 감싸져 있어도 ASSET_SERVICE_UNAVAILABLE로 변환한다")
    void mapsWrappedOpenCircuitToServiceUnavailable() {
        RuntimeException wrapped = new RuntimeException("wrapper", circuitOpen());

        assertThatThrownBy(() -> AssetServiceCaller.call(() -> {
            throw wrapped;
        }, SettlementErrorCode.ASSET_REVENUE_NOT_FOUND))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.ASSET_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("404는 기존대로 지정한 NOT_FOUND 에러로 변환한다")
    void keepsNotFoundTranslation() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);

        assertThatThrownBy(() -> AssetServiceCaller.call(() -> {
            throw notFound;
        }, SettlementErrorCode.ASSET_REVENUE_NOT_FOUND))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.ASSET_REVENUE_NOT_FOUND));
    }

    @Test
    @DisplayName("서킷과 무관한 예외는 변환 없이 그대로 전파한다")
    void propagatesOtherExceptionsUnchanged() {
        IllegalStateException other = new IllegalStateException("boom");

        assertThatThrownBy(() -> AssetServiceCaller.call(() -> {
            throw other;
        }, SettlementErrorCode.ASSET_REVENUE_NOT_FOUND))
                .isSameAs(other);
    }
}