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

    REVENUE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_01", "해당 자산에 수익을 등록할 권한이 없습니다."),

    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_01", "존재하지 않는 자산입니다."),
    REVENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_02", "존재하지 않는 수익입니다."),

    INSUFFICIENT_HOLDING_QUANTITY(HttpStatus.CONFLICT, "ASSET_409_01", "보유 수량보다 많은 지분을 회수할 수 없습니다."),
    HOLDING_DATA_CONFLICT(HttpStatus.CONFLICT, "ASSET_409_02", "지분 처리 이력과 보유지분 데이터가 일치하지 않습니다."),
    ASSET_NOT_AVAILABLE(HttpStatus.CONFLICT, "ASSET_409_03", "승인된 자산만 지분을 배정할 수 있습니다."),
    SHARE_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "ASSET_409_04", "남은 발행 수량보다 많은 지분을 배정할 수 없습니다."),
    INSUFFICIENT_ALLOCATED_QUANTITY(HttpStatus.CONFLICT, "ASSET_409_05", "현재 배정된 수량보다 많은 지분을 회수할 수 없습니다."),
    DUPLICATE_REVENUE(HttpStatus.CONFLICT, "ASSET_409_06", "이미 등록된 수익 데이터입니다.");

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
