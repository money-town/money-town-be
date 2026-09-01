ALTER TABLE p_wallets DROP CONSTRAINT ck_wallets_deletion;

ALTER TABLE p_wallets ADD CONSTRAINT ck_wallets_deletion
    CHECK ((is_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
        OR (is_deleted = TRUE AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL));