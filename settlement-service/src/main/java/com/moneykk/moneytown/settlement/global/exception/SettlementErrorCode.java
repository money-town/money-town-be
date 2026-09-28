package com.moneykk.moneytown.settlement.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SettlementErrorCode implements ErrorCode {

    ASSET_REVENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_404_01", "자산 서비스에서 수익 데이터를 찾을 수 없습니다."),
    ASSET_HOLDINGS_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_404_02", "자산 서비스에서 보유지분 스냅샷을 조회할 수 없습니다."),
    SETTLEMENT_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_404_03", "정산 회차를 찾을 수 없습니다."),
    FINAL_SETTLEMENT_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_404_04", "최종 정산 회차를 찾을 수 없습니다."),

    REVENUE_ASSET_MISMATCH(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_01", "수익 데이터의 자산과 요청한 자산이 일치하지 않습니다."),
    REVENUE_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_03", "수익 금액(총수익/비용/수수료)이 올바르지 않습니다."),

    SETTLEMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SETTLEMENT_403_01", "정산 회차 관련 기능은 ADMIN 권한으로만 이용할 수 있습니다."),
    FINAL_SETTLEMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SETTLEMENT_403_02", "최종 정산 회차 관련 기능은 ADMIN 권한으로만 이용할 수 있습니다."),
    FINAL_SETTLEMENT_SYSTEM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SETTLEMENT_403_03", "최종 정산 회차 개시는 SYSTEM 권한(자산 서비스)만 호출할 수 있습니다."),
    DIVIDEND_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SETTLEMENT_403_04", "내 배당 내역 조회는 INVESTOR 또는 ISSUER 권한으로만 이용할 수 있습니다."),

    REVENUE_NOT_READY(HttpStatus.CONFLICT, "SETTLEMENT_409_01", "정산 전달 대기(READY) 상태의 수익이 아닙니다."),
    DISTRIBUTABLE_AMOUNT_NOT_POSITIVE(HttpStatus.CONFLICT, "SETTLEMENT_409_02", "배당 가능 총액이 0원 이하라 정산 회차를 개시할 수 없습니다."),
    SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE(HttpStatus.CONFLICT, "SETTLEMENT_409_03", "이미 해당 수익 건으로 개시된 정산 회차가 존재합니다."),
    SETTLEMENT_IN_PROGRESS_FOR_ASSET(HttpStatus.CONFLICT, "SETTLEMENT_409_04", "해당 자산은 이미 진행 중인 정산 회차가 있어 순차적으로 처리해야 합니다."),
    HOLDING_SNAPSHOT_INVALID(HttpStatus.CONFLICT, "SETTLEMENT_409_05", "보유지분 스냅샷의 전체 발행 지분 수량이 올바르지 않습니다."),
    SETTLEMENT_BATCH_NOT_RETRYABLE(HttpStatus.CONFLICT, "SETTLEMENT_409_06", "실패(FAILED) 또는 부분 실패(PARTIAL_FAILED) 상태의 정산 회차만 재시도할 수 있습니다."),
    FINAL_SETTLEMENT_HOLDERS_NOT_FOUND(HttpStatus.CONFLICT, "SETTLEMENT_409_07", "최종 정산 시점(terminatedAt) 기준 보유자가 존재하지 않습니다."),
    FINAL_SETTLEMENT_BATCH_NOT_RETRYABLE(HttpStatus.CONFLICT, "SETTLEMENT_409_08", "실패(FAILED) 또는 부분 실패(PARTIAL_FAILED) 상태의 최종 정산 회차만 재시도할 수 있습니다."),
    FINAL_SETTLEMENT_NO_RETRYABLE_PAYOUTS(HttpStatus.CONFLICT, "SETTLEMENT_409_09", "재처리 가능한 실패 건이 존재하지 않습니다."),
    PAYOUT_NOT_ABANDONABLE(HttpStatus.CONFLICT, "SETTLEMENT_409_10", "DEAD_LETTER 상태의 지급 건만 포기 처리할 수 있습니다."),
    PAYOUT_BATCH_MISMATCH(HttpStatus.CONFLICT, "SETTLEMENT_409_11", "지급 건이 요청한 정산 회차에 속하지 않습니다."),
    RESOLUTION_NOTE_REQUIRED(HttpStatus.CONFLICT, "SETTLEMENT_409_12", "OTHER 방식으로 포기 처리하려면 상세 설명(resolutionNote)이 필수입니다."),
    FINAL_SETTLEMENT_PAYOUT_NOT_ABANDONABLE(HttpStatus.CONFLICT, "SETTLEMENT_409_13", "DEAD_LETTER 상태의 최종 정산 지급 건만 포기 처리할 수 있습니다."),
    FINAL_SETTLEMENT_PAYOUT_BATCH_MISMATCH(HttpStatus.CONFLICT, "SETTLEMENT_409_14", "지급 건이 요청한 최종 정산 회차에 속하지 않습니다."),
    FINAL_SETTLEMENT_PAYOUT_MANUAL_RESOLUTION_REQUIRED(HttpStatus.CONFLICT, "SETTLEMENT_409_15",
            "지갑 응답 불일치(RESPONSE_MISMATCH)로 실패한 지급 건은 재처리할 수 없습니다. 지갑 트랜잭션을 대조한 뒤 수동 지급하고 수동 지급(abandon) 처리해야 합니다."),

    ASSET_HOLDINGS_PAGINATION_STALLED(HttpStatus.INTERNAL_SERVER_ERROR, "SETTLEMENT_500_01",
            "자산 서비스의 보유지분 스냅샷 페이지네이션이 비정상적으로 종료되지 않습니다."),

    ASSET_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SETTLEMENT_503_01",
            "자산 서비스가 일시적으로 응답하지 않아 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");

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