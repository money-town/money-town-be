package com.moneykk.moneytown.asset.entity;

public enum RevenueTransferStatus {
    /** 정산 서비스로 전달되기를 기다리는 상태 */
    READY,
    /** 정산 서비스에 정상적으로 전달된 상태 */
    TRANSFERRED,
    /** 정산 서비스 전달에 실패한 상태 */
    FAILED
}
