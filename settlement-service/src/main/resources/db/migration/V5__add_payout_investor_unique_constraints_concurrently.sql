-- CREATE INDEX CONCURRENTLY는 트랜잭션 내부에서 실행할 수 없다.
-- spring.flyway.mixed=true 설정으로 이 마이그레이션 전체가 트랜잭션 없이 실행되도록 한다.
-- ACCESS EXCLUSIVE 잠금 없이 기존 행 검증 + 인덱스 생성을 수행한다.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_dividend_payouts_batch_investor
    ON p_dividend_payouts (settlement_batch_id, investor_id);

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_final_settlement_payouts_batch_investor
    ON p_final_settlement_payouts (final_settlement_batch_id, investor_id);

-- 이미 만들어진 인덱스를 제약으로 승격 (메타데이터만 변경하는 짧은 잠금)
ALTER TABLE p_dividend_payouts
    ADD CONSTRAINT uk_dividend_payouts_batch_investor UNIQUE USING INDEX uk_dividend_payouts_batch_investor;

ALTER TABLE p_final_settlement_payouts
    ADD CONSTRAINT uk_final_settlement_payouts_batch_investor UNIQUE USING INDEX uk_final_settlement_payouts_batch_investor;