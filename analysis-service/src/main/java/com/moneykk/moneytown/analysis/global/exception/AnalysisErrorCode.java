package com.moneykk.moneytown.analysis.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AnalysisErrorCode implements ErrorCode {

    // ===== FDS =====
    FDS_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "FDS_400_01", "요청 값이 올바르지 않습니다."),
    FDS_STATE_NOT_FOUND(HttpStatus.NOT_FOUND, "FDS_404_01", "대상 사용자의 FDS 상태 정보가 존재하지 않습니다."),
    FDS_ALREADY_NORMAL(HttpStatus.CONFLICT, "FDS_409_01", "이미 정상 상태인 사용자입니다."),
    FDS_ALREADY_BLOCKED(HttpStatus.CONFLICT, "FDS_409_02", "이미 차단된 사용자입니다."),
    FDS_FORBIDDEN(HttpStatus.FORBIDDEN, "FDS_403_01", "FDS 접근 권한이 없습니다."),



    // ====== Notification ======
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI_404_01", "해당하는 알림 정보가 존재하지 않습니다."),
    NOTIFICATION_ALREADY_FINISHED(HttpStatus.CONFLICT, "NOTI_409_01", "이미 처리된 알림입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;


    AnalysisErrorCode(HttpStatus status, String code, String message){
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
