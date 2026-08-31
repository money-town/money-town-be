package com.moneykk.moneytown.asset.entity;

public enum HoldingHistoryType {
    /** 확정된 청약 수량을 투자자에게 배정 */
    ALLOCATE,
    /** 취소된 청약 등에 대해 이미 배정된 지분을 회수 */
    REVOKE,
    /** 투자자 사이에서 지분을 이전 */
    TRANSFER,
    /** 관리자가 정합성 문제를 해결하기 위해 지분을 보정 */
    ADJUSTMENT
}
