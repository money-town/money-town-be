package com.moneykk.moneytown.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.CommonErrorCode;
import com.moneykk.moneytown.common.exception.ErrorCode;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeignExceptionTranslatorTest {

    @Test
    @DisplayName("정상 호출 결과를 그대로 반환한다")
    void returnsSuccessfulResult() {
        // when
        String result = FeignExceptionTranslator.call(
                () -> "success",
                CommonErrorCode.INVALID_INPUT_VALUE
        );

        // then
        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("404 FeignException을 지정한 BusinessException으로 변환하고 원인을 보존한다")
    void translatesNotFoundAndPreservesCause() {
        // given
        FeignException.NotFound cause =
                mock(FeignException.NotFound.class);

        ErrorCode notFoundError =
                CommonErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertThatThrownBy(() ->
                FeignExceptionTranslator.call(
                        () -> {
                            throw cause;
                        },
                        notFoundError
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isSameAs(notFoundError);

                            assertThat(exception.getCause())
                                    .isSameAs(cause);
                        }
                );
    }

    @Test
    @DisplayName("세 인자 호출에서도 404 FeignException의 원인을 보존한다")
    void translatesNotFoundWithInvalidRequestError() {
        // given
        FeignException.NotFound cause =
                mock(FeignException.NotFound.class);

        ErrorCode notFoundError =
                CommonErrorCode.INVALID_SORT_PROPERTY;

        ErrorCode invalidRequestError =
                CommonErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertThatThrownBy(() ->
                FeignExceptionTranslator.call(
                        () -> {
                            throw cause;
                        },
                        notFoundError,
                        invalidRequestError
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isSameAs(notFoundError);

                            assertThat(exception.getCause())
                                    .isSameAs(cause);
                        }
                );
    }

    @Test
    @DisplayName("400 FeignException을 지정한 BusinessException으로 변환하고 원인을 보존한다")
    void translatesBadRequestAndPreservesCause() {
        // given
        FeignException.BadRequest cause =
                mock(FeignException.BadRequest.class);

        ErrorCode notFoundError =
                CommonErrorCode.INVALID_SORT_PROPERTY;

        ErrorCode invalidRequestError =
                CommonErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertThatThrownBy(() ->
                FeignExceptionTranslator.call(
                        () -> {
                            throw cause;
                        },
                        notFoundError,
                        invalidRequestError
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isSameAs(invalidRequestError);

                            assertThat(exception.getCause())
                                    .isSameAs(cause);
                        }
                );
    }

    @Test
    @DisplayName("422 FeignException을 지정한 BusinessException으로 변환하고 원인을 보존한다")
    void translatesUnprocessableEntityAndPreservesCause() {
        // given
        FeignException.UnprocessableEntity cause =
                mock(FeignException.UnprocessableEntity.class);

        ErrorCode notFoundError =
                CommonErrorCode.INVALID_SORT_PROPERTY;

        ErrorCode invalidRequestError =
                CommonErrorCode.INVALID_INPUT_VALUE;

        // when & then
        assertThatThrownBy(() ->
                FeignExceptionTranslator.call(
                        () -> {
                            throw cause;
                        },
                        notFoundError,
                        invalidRequestError
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isSameAs(invalidRequestError);

                            assertThat(exception.getCause())
                                    .isSameAs(cause);
                        }
                );
    }
}