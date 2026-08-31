package com.moneykk.moneytown.asset.entity;

public enum DocumentType {
    /** 투자 대상과 조건을 설명하는 투자 안내서 */
    INVESTMENT_GUIDE,
    /** 자산의 평가금액을 증명하는 감정평가서 */
    APPRAISAL,
    /** 자산에 대한 소유권이나 권리를 증명하는 문서 */
    RIGHT_PROOF,
    /** 위 유형에 속하지 않는 기타 문서 */
    ETC
}
