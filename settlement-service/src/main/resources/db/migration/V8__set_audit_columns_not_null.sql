-- common-module의 AuditorAware가 이제 SYSTEM 시드 UUID를 fallback으로 반환해
-- created_by/updated_by가 항상 채워지므로, ERD 스펙대로 NOT NULL을 강제한다.
-- 이 시점 테이블은 항상 비어있어(V2 직후) 전체 스캔 비용도 무시할 수준이다.
ALTER TABLE p_settlement_batches
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE p_holdings_snapshots
    ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE p_dividend_payouts
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE p_final_settlement_batches
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE p_final_settlement_payouts
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;