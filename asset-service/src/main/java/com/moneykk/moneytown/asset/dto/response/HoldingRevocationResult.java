package com.moneykk.moneytown.asset.dto.response;

/** 지분 회수 결과 */
public enum HoldingRevocationResult {

    // 지분 회수 완료
    REVOKED,

    // 회수할 지분이 없거나 이미 회수됨
    NO_ACTION,

    // 데이터 불일치로 회수 실패
    FAILED
}