package com.moneykk.moneytown.common.client;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.ErrorCode;
import feign.FeignException;

import java.util.function.Supplier;

public final class FeignExceptionTranslator {

    private FeignExceptionTranslator() {
    }

    public static <T> T call(Supplier<T> feignCall, ErrorCode notFoundError) {
        try {
            return feignCall.get();
        } catch (FeignException.NotFound e) {
            throw new BusinessException(notFoundError);
        }
    }

    public static <T> T call(Supplier<T> feignCall, ErrorCode notFoundError, ErrorCode invalidRequestError) {
        try {
            return feignCall.get();
        } catch (FeignException.NotFound e) {
            throw new BusinessException(notFoundError);
        } catch (FeignException.BadRequest | FeignException.UnprocessableEntity e) {
            throw new BusinessException(invalidRequestError);
        }
    }
}