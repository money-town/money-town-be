-- V6에서 NOT VALID로 추가한 FK를 검증한다. VALIDATE CONSTRAINT는 SHARE UPDATE EXCLUSIVE 잠금만 사용해
-- 읽기/쓰기를 막지 않고, V6와 별도 트랜잭션(별도 마이그레이션 파일)에서 실행되어야 그 효과가 있다.
ALTER TABLE p_holdings_snapshots
    VALIDATE CONSTRAINT fk_holdings_snapshots_settlement_batch;

ALTER TABLE p_dividend_payouts
    VALIDATE CONSTRAINT fk_dividend_payouts_settlement_batch;

ALTER TABLE p_final_settlement_payouts
    VALIDATE CONSTRAINT fk_final_settlement_payouts_final_settlement_batch;