package com.moneykk.moneytown.offering.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SubscriptionErrorCode implements ErrorCode {

    // 400 BAD_REQUEST
    INVALID_SUBSCRIPTION_INPUT(
            HttpStatus.BAD_REQUEST,
            "SUBSCRIPTION_400_01",
            "청약 정보가 올바르지 않습니다."
    ),
    INVALID_SUBSCRIPTION_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "SUBSCRIPTION_400_02",
            "청약 수량이 올바르지 않습니다."
    ),
    INVALID_IDEMPOTENCY_KEY(
            HttpStatus.BAD_REQUEST,
            "SUBSCRIPTION_400_03",
            "Idempotency-Key가 올바르지 않습니다."
    ),
    INVALID_SUBSCRIPTION_SEARCH_CONDITION(
            HttpStatus.BAD_REQUEST,
            "SUBSCRIPTION_400_04",
            "청약 검색 조건이 올바르지 않습니다."
    ),
    INVALID_SUBSCRIPTION_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "SUBSCRIPTION_400_05",
            "청약 금액이 허용 범위를 초과했습니다."
    ),

    // 403 FORBIDDEN
    SUBSCRIPTION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "SUBSCRIPTION_403_01",
            "해당 청약에 대한 권한이 없습니다."
    ),
    SUBSCRIPTION_ELIGIBILITY_NOT_MET(
            HttpStatus.FORBIDDEN,
            "SUBSCRIPTION_403_02",
            "청약 자격을 충족하지 않습니다."
    ),
    SUBSCRIPTION_BLOCKED_BY_FDS(
            HttpStatus.FORBIDDEN,
            "SUBSCRIPTION_403_03",
            "이상 거래 탐지 정책에 의해 청약이 제한되었습니다."
    ),

    // 404 NOT_FOUND
    SUBSCRIPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUBSCRIPTION_404_01",
            "청약을 찾을 수 없습니다."
    ),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUBSCRIPTION_404_02",
            "사용자를 찾을 수 없습니다."
    ),

    // 409 CONFLICT
    DUPLICATE_SUBSCRIPTION(
            HttpStatus.CONFLICT,
            "SUBSCRIPTION_409_01",
            "이미 청약한 공모입니다."
    ),
    IDEMPOTENCY_KEY_CONFLICT(
            HttpStatus.CONFLICT,
            "SUBSCRIPTION_409_02",
            "동일한 Idempotency-Key에 다른 청약 요청이 전달되었습니다."
    ),
    IDEMPOTENCY_REQUEST_PROCESSING(
            HttpStatus.CONFLICT,
            "SUBSCRIPTION_409_03",
            "동일한 청약 요청이 현재 처리 중입니다."
    ),
    IDEMPOTENCY_REQUEST_FAILED(
            HttpStatus.CONFLICT,
            "SUBSCRIPTION_409_04",
            "이전에 실패한 청약 요청입니다."
    ),
    SUBSCRIPTION_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "SUBSCRIPTION_409_05",
            "현재 청약 가능한 공모가 아닙니다."
    ),
    INSUFFICIENT_REMAINING_QUANTITY(
            HttpStatus.CONFLICT,
            "SUBSCRIPTION_409_06",
            "청약 가능한 잔여 수량이 부족합니다."
    ),

    // 503 SERVICE_UNAVAILABLE
    FDS_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SUBSCRIPTION_503_01",
            "현재 청약 검증 서비스를 사용할 수 없습니다."
    ),
    USER_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SUBSCRIPTION_503_02",
            "사용자 상태 조회 서비스를 사용할 수 없습니다."
    ),

    // 500 INTERNAL_SERVER_ERROR
    IDEMPOTENCY_REQUEST_STATE_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SUBSCRIPTION_500_01",
            "멱등 요청 상태가 올바르지 않습니다."
    ),
    IDEMPOTENCY_COMPLETION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SUBSCRIPTION_500_02",
            "멱등 요청 완료 처리에 실패했습니다."
    ),
    EXTERNAL_RESPONSE_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SUBSCRIPTION_500_03",
            "외부 서비스 응답이 올바르지 않습니다."
    ),
    REQUEST_HASH_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SUBSCRIPTION_500_04",
            "청약 요청 해시 생성에 실패했습니다."
    );
    private final HttpStatus status;
    private final String code;
    private final String message;

    SubscriptionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
