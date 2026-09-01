-- common-module의 AuditorAware가 이제 SYSTEM 시드 UUID를 fallback으로 반환해
-- created_by/updated_by가 항상 채워지므로, ERD 스펙대로 NOT NULL을 강제한다.
-- SET NOT NULL 전에, NULL로 남아있을 수 있는 기존 값을 SYSTEM_USER_ID(JpaAuditingConfig.SYSTEM_USER_ID와 동일한 nil UUID)로 보정한다.
UPDATE p_settlement_batches SET created_by = '00000000-0000-0000-0000-000000000000' WHERE created_by IS NULL;
UPDATE p_settlement_batches SET updated_by = '00000000-0000-0000-0000-000000000000' WHERE updated_by IS NULL;

UPDATE p_holdings_snapshots SET created_by = '00000000-0000-0000-0000-000000000000' WHERE created_by IS NULL;

UPDATE p_dividend_payouts SET created_by = '00000000-0000-0000-0000-000000000000' WHERE created_by IS NULL;
UPDATE p_dividend_payouts SET updated_by = '00000000-0000-0000-0000-000000000000' WHERE updated_by IS NULL;

UPDATE p_final_settlement_batches SET created_by = '00000000-0000-0000-0000-000000000000' WHERE created_by IS NULL;
UPDATE p_final_settlement_batches SET updated_by = '00000000-0000-0000-0000-000000000000' WHERE updated_by IS NULL;

UPDATE p_final_settlement_payouts SET created_by = '00000000-0000-0000-0000-000000000000' WHERE created_by IS NULL;
UPDATE p_final_settlement_payouts SET updated_by = '00000000-0000-0000-0000-000000000000' WHERE updated_by IS NULL;

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