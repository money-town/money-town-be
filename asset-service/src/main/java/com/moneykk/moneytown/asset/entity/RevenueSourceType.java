package com.moneykk.moneytown.asset.entity;

public enum RevenueSourceType {
    /** 부동산 임대관리 시스템에서 전달된 수익 */
    PROPERTY_MANAGER,
    /** 음원 저작권 신탁 시스템에서 전달된 수익 */
    MUSIC_TRUST,
    /** 관리자가 직접 등록한 수익 */
    ADMIN,
    /** 테스트나 시뮬레이션을 위해 생성된 합성 수익 */
    SYNTHETIC
}
