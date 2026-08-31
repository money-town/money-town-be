package com.moneykk.moneytown.settlement.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SettlementErrorCode implements ErrorCode {

    ASSET_REVENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_404_01", "자산 서비스에서 수익 데이터를 찾을 수 없습니다."),
    ASSET_HOLDINGS_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_404_02", "자산 서비스에서 보유지분 스냅샷을 조회할 수 없습니다."),

    REVENUE_ASSET_MISMATCH(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_01", "수익 데이터의 자산과 요청한 자산이 일치하지 않습니다."),
    REVENUE_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_02", "배당 기준일(recordDate)이 수익 발생 시각(occurredAt)보다 앞설 수 없습니다."),
    REVENUE_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_03", "수익 금액(총수익/비용/수수료)이 올바르지 않습니다."),

    REVENUE_NOT_READY(HttpStatus.CONFLICT, "SETTLEMENT_409_01", "정산 전달 대기(PENDING) 상태의 수익이 아닙니다."),
    DISTRIBUTABLE_AMOUNT_NOT_POSITIVE(HttpStatus.CONFLICT, "SETTLEMENT_409_02", "배당 가능 총액이 0원 이하라 정산 회차를 개시할 수 없습니다."),
    SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE(HttpStatus.CONFLICT, "SETTLEMENT_409_03", "이미 해당 수익 건으로 개시된 정산 회차가 존재합니다."),
    SETTLEMENT_IN_PROGRESS_FOR_ASSET(HttpStatus.CONFLICT, "SETTLEMENT_409_04", "해당 자산은 이미 진행 중인 정산 회차가 있어 순차적으로 처리해야 합니다."),
    HOLDING_SNAPSHOT_INVALID(HttpStatus.CONFLICT, "SETTLEMENT_409_05", "보유지분 스냅샷의 전체 발행 지분 수량이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SettlementErrorCode(HttpStatus status, String code, String message) {
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