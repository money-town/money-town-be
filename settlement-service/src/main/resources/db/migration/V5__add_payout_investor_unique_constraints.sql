-- CREATE INDEX CONCURRENTLY 방식은 Flyway가 마이그레이션 내내 별도 커넥션으로 잡아두는
-- 트랜잭션과 서로를 기다리며 데드락에 빠지는 것이 확인되어 포기했다.
-- 이 시점(V2 직후)에는 항상 빈 테이블이라 검증+인덱스 생성 비용이 무시할 수준이므로
-- 일반 ALTER TABLE로 되돌린다.
ALTER TABLE p_dividend_payouts
    ADD CONSTRAINT uk_dividend_payouts_batch_investor UNIQUE (settlement_batch_id, investor_id);

ALTER TABLE p_final_settlement_payouts
    ADD CONSTRAINT uk_final_settlement_payouts_batch_investor UNIQUE (final_settlement_batch_id, investor_id);