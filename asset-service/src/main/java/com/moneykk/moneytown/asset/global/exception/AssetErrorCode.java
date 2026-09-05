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
    INVALID_ASSET_CURSOR(HttpStatus.BAD_REQUEST, "ASSET_400_10", "유효하지 않은 자산 조회 커서입니다."),
    INVALID_REVENUE_CURSOR(HttpStatus.BAD_REQUEST, "ASSET_400_11", "유효하지 않은 수익 조회 커서입니다."),
    INVALID_HOLDING_CURSOR(HttpStatus.BAD_REQUEST, "ASSET_400_12", "유효하지 않은 보유지분 조회 커서입니다."),
    INVALID_ASSET_SHARE_PRICE(HttpStatus.BAD_REQUEST, "ASSET_400_13", "평가 금액과 전체 지분 수량은 양수이며, 계산된 지분 단가는 최소 1원이어야 합니다."),
    APPRAISAL_AMOUNT_UPDATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ASSET_400_14", "자산 평가금액은 수정할 수 없습니다."),
    ASSET_REJECTION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "ASSET_400_15", "자산 반려 시 반려 사유는 필수입니다."),
    INVALID_ASSET_DOCUMENT_CURSOR(HttpStatus.BAD_REQUEST, "ASSET_400_16", "유효하지 않은 자산 문서 조회 커서입니다."),

    REVENUE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_01", "해당 자산에 수익을 등록할 권한이 없습니다."),
    HOLDING_SNAPSHOT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_02", "지분 스냅샷은 SYSTEM 권한으로만 조회할 수 있습니다."),
    ASSET_CREATE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_03", "자산을 등록할 권한이 없습니다."),
    ASSET_READ_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_04", "자산을 조회할 권한이 없습니다."),
    REVENUE_TRANSFER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_05", "수익 전달 상태는 SYSTEM 권한으로만 변경할 수 있습니다."),
    ASSET_UPDATE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_06", "자산을 수정할 권한이 없습니다."),
    ASSET_STATUS_CHANGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_07", "자산 상태를 변경할 권한이 없습니다."),
    ASSET_DELETE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_08", "자산을 삭제할 권한이 없습니다."),
    ASSET_DOCUMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_09", "자산 문서를 관리할 권한이 없습니다."),
    HOLDING_READ_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ASSET_403_10", "내 보유지분을 조회할 권한이 없습니다."),

    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_01", "존재하지 않는 자산입니다."),
    REVENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_02", "존재하지 않는 수익입니다."),
    ASSET_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_03", "존재하지 않는 자산 문서입니다."),
    HOLDING_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_04", "존재하지 않는 보유지분입니다."),

    INSUFFICIENT_HOLDING_QUANTITY(HttpStatus.CONFLICT, "ASSET_409_01", "보유 수량보다 많은 지분을 회수할 수 없습니다."),
    HOLDING_DATA_CONFLICT(HttpStatus.CONFLICT, "ASSET_409_02", "지분 처리 이력과 보유지분 데이터가 일치하지 않습니다."),
    ASSET_NOT_AVAILABLE(HttpStatus.CONFLICT, "ASSET_409_03", "승인된 자산만 지분을 배정할 수 있습니다."),
    SHARE_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "ASSET_409_04", "남은 발행 수량보다 많은 지분을 배정할 수 없습니다."),
    INSUFFICIENT_ALLOCATED_QUANTITY(HttpStatus.CONFLICT, "ASSET_409_05", "현재 배정된 수량보다 많은 지분을 회수할 수 없습니다."),
    DUPLICATE_REVENUE(HttpStatus.CONFLICT, "ASSET_409_06", "이미 등록된 수익 데이터입니다."),
    INVALID_REVENUE_TRANSFER_STATUS(HttpStatus.CONFLICT, "ASSET_409_07", "전달 완료된 수익의 상태는 되돌릴 수 없습니다."),
    ASSET_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "ASSET_409_08", "현재 상태에서는 자산을 수정할 수 없습니다."),
    INVALID_ASSET_STATUS_TRANSITION(HttpStatus.CONFLICT, "ASSET_409_09", "허용되지 않은 자산 상태 변경입니다."),
    ASSET_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "ASSET_409_10", "작성 중이거나 반려된 자산만 삭제할 수 있습니다."),

    ASSET_DOCUMENT_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ASSET_500_01", "자산 문서 저장소 처리에 실패했습니다.");

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
