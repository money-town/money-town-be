package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.client.FeignExceptionTranslator;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.ErrorCode;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.util.function.Supplier;

// FeignExceptionTranslator는 404 등 일부 FeignException만 변환하므로, 서킷이 열려 호출이 거부된 경우는
// 여기서 별도로 "자산 서비스 장애(503)"로 변환한다. 공통 모듈은 settlement만 쓰는 서킷브레이커 의존성을 갖지 않게 이 서비스에 둔다.
public final class AssetServiceCaller {

    private AssetServiceCaller() {
    }

    public static <T> T call(Supplier<T> assetCall, ErrorCode notFoundError) {
        try {
            return FeignExceptionTranslator.call(assetCall, notFoundError);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isCircuitOpen(e)) {
                throw new BusinessException(SettlementErrorCode.ASSET_SERVICE_UNAVAILABLE, e);
            }
            throw e;
        }
    }

    // OpenFeign 서킷브레이커 래퍼가 원인 예외로 감싸서 던질 수 있어 원인 체인까지 확인한다.
    private static boolean isCircuitOpen(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof CallNotPermittedException) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}