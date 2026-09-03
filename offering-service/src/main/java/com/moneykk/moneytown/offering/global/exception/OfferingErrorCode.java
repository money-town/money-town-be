package com.moneykk.moneytown.offering.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OfferingErrorCode implements ErrorCode {

    // 400 BAD_REQUEST
    INVALID_OFFERING_INPUT(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_01",
            "공모 정보가 올바르지 않습니다."
    ),
    INVALID_OFFERING_TITLE(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_02",
            "공모 상품명이 올바르지 않습니다."
    ),
    INVALID_OFFERING_PRICE(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_03",
            "단위당 청약 가격은 0보다 커야 합니다."
    ),
    INVALID_OFFERING_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_04",
            "공모 수량이 올바르지 않습니다."
    ),
    INVALID_SUBSCRIPTION_QUANTITY_RANGE(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_05",
            "최소·최대 청약 수량이 올바르지 않습니다."
    ),
    INVALID_OFFERING_PERIOD(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_06",
            "공모 모집 기간이 올바르지 않습니다."
    ),
    INVALID_REJECTION_REASON(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_07",
            "공모 반려 사유가 올바르지 않습니다."
    ),
    INVALID_OFFERING_SEARCH_CONDITION(
            HttpStatus.BAD_REQUEST,
            "OFFERING_400_08",
            "공모 검색 조건이 올바르지 않습니다."
    ),

    // 403 FORBIDDEN
    OFFERING_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "OFFERING_403_01",
            "해당 공모에 대한 권한이 없습니다."
    ),
    OFFERING_REVIEW_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "OFFERING_403_02",
            "공모 심사 권한이 없습니다."
    ),
    OFFERING_MANAGEMENT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "OFFERING_403_03",
            "공모 관리 권한이 없습니다."
    ),
    OFFERING_ASSET_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "OFFERING_403_04",
            "해당 자산에 대한 권한이 없습니다."
    ),

    // 404 NOT_FOUND
    OFFERING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "OFFERING_404_01",
            "공모를 찾을 수 없습니다."
    ),
    OFFERING_ASSET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "OFFERING_404_02",
            "공모 대상 자산을 찾을 수 없습니다."
    ),

    // 409 CONFLICT
    OFFERING_REVIEW_REQUEST_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "OFFERING_409_01",
            "심사를 요청할 수 없는 공모 상태입니다."
    ),
    OFFERING_APPROVAL_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "OFFERING_409_02",
            "승인할 수 없는 공모 상태입니다."
    ),
    OFFERING_REJECTION_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "OFFERING_409_03",
            "반려할 수 없는 공모 상태입니다."
    ),
    OFFERING_UPDATE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "OFFERING_409_04",
            "수정할 수 없는 공모 상태입니다."
    ),
    OFFERING_DELETE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "OFFERING_409_05",
            "현재 상태에서는 공모를 삭제할 수 없습니다."
    ),
    OFFERING_PERIOD_EXPIRED(
            HttpStatus.CONFLICT,
            "OFFERING_409_06",
            "이미 종료된 모집 기간의 공모는 처리할 수 없습니다."
    ),
    OFFERING_QUANTITY_STATE_INVALID(
            HttpStatus.CONFLICT,
            "OFFERING_409_07",
            "공모의 잔여 모집 수량 상태가 올바르지 않습니다."
    ),
    OFFERING_HAS_SUBSCRIPTIONS(
            HttpStatus.CONFLICT,
            "OFFERING_409_08",
            "청약 이력이 존재하는 공모는 삭제할 수 없습니다."
    ),
    OFFERING_ASSET_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
        "OFFERING_409_09",
                "현재 상태의 자산으로 공모를 생성할 수 없습니다."
    ),
    OFFERING_QUANTITY_EXCEEDS_AVAILABLE(
            HttpStatus.CONFLICT,
            "OFFERING_409_10",
            "공모 모집 수량이 현재 공모 가능한 지분 수량을 초과했습니다."
    ),

    // 500
    ASSET_RESPONSE_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "OFFERING_500_01",
            "자산 서비스 응답이 올바르지 않습니다."
    ),

    ASSET_QUANTITY_STATE_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "OFFERING_500_02",
            "자산 지분 수량 상태가 올바르지 않습니다."
    ),

    // 503
    ASSET_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "OFFERING_503_01",
            "현재 자산 조회 서비스를 사용할 수 없습니다."
    )

    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

    OfferingErrorCode(HttpStatus status, String code, String message) {
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
