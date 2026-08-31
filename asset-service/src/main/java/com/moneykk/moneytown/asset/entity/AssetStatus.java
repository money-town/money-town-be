package com.moneykk.moneytown.asset.entity;

public enum AssetStatus {
    /** 자산 정보를 작성 중인 상태 */
    DRAFT,
    /** 자산운용자가 관리자에게 심사를 요청한 상태 */
    REVIEW_REQUESTED,
    /** 관리자의 심사를 통과해 운영할 수 있는 상태 */
    APPROVED,
    /** 심사에서 반려되어 수정이 필요한 상태 */
    REJECTED,
    /** 관리자가 자산 운영을 일시 중단한 상태 */
    SUSPENDED,
    /** 최종 정산까지 완료되어 운영이 종료된 상태 */
    TERMINATED
}
