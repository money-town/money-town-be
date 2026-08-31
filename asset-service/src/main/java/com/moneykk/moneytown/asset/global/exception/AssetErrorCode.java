package com.moneykk.moneytown.asset.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AssetErrorCode implements ErrorCode {
    INVALID_ASSET_DOCUMENT(HttpStatus.BAD_REQUEST, "ASSET_400_01", "자산 문서 정보가 올바르지 않습니다."),
    INVALID_HOLDING_QUANTITY(HttpStatus.BAD_REQUEST, "ASSET_400_02", "지분 수량이 올바르지 않습니다."),
    INVALID_HOLDING_HISTORY(HttpStatus.BAD_REQUEST, "ASSET_400_03", "지분 변동 이력 정보가 올바르지 않습니다."),
    SUBSCRIPTION_REQUIRED(HttpStatus.BAD_REQUEST, "ASSET_400_04", "배정·회수 이력에는 청약 ID가 필요합니다."),
    HOLDING_HISTORY_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "ASSET_400_05", "배정 외 지분 변동에는 사유가 필요합니다."),
    INVALID_REVENUE_AMOUNT(HttpStatus.BAD_REQUEST, "ASSET_400_06", "수익과 비용 금액이 올바르지 않습니다."),
    INVALID_REVENUE_PERIOD(HttpStatus.BAD_REQUEST, "ASSET_400_07", "수익 발생 기간이 올바르지 않습니다."),
    UNSUPPORTED_CURRENCY(HttpStatus.BAD_REQUEST, "ASSET_400_08", "MVP에서는 KRW만 지원합니다."),
    REVENUE_FAILURE_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "ASSET_400_09", "전달 실패 사유가 필요합니다."),
    INSUFFICIENT_HOLDING_QUANTITY(HttpStatus.CONFLICT, "ASSET_409_01", "보유 수량보다 많은 지분을 회수할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AssetErrorCode(HttpStatus status, String code, String message) {
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
