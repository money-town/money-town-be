-- DEAD_LETTER 사유 구분. 지갑 응답 불일치(RESPONSE_MISMATCH)는 재시도해도 해결되지 않고 지갑 트랜잭션 대조 후
-- 관리자가 수동 지급(abandon)해야 하므로, 일반 재시도 초과(RETRY_EXCEEDED)와 DB에서 구분할 수 있어야 한다.
-- 기존 DEAD_LETTER 행은 사유를 알 수 없어 NULL로 남으며, NULL은 기존처럼 재시도 가능으로 취급한다.
ALTER TABLE p_final_settlement_payouts
    ADD COLUMN dead_letter_reason VARCHAR(30);