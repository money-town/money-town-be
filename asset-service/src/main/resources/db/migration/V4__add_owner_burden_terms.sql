-- 기존 자산에 소유주의 동의나 공모 완료 시각을 임의로 소급 적용하지 않는다.
ALTER TABLE p_assets
    ADD COLUMN owner_burden_payment_method VARCHAR(30),
    ADD COLUMN completed_offering_id UUID,
    ADD COLUMN offering_completed_at TIMESTAMPTZ,
    ADD COLUMN owner_burden_principal BIGINT,
    ADD CONSTRAINT ck_assets_owner_burden_method CHECK (
        owner_burden_payment_method IN ('SALE_DEDUCTION', 'WALLET_PAYMENT')
    ),
    ADD CONSTRAINT ck_assets_owner_burden_completion CHECK (
        (completed_offering_id IS NULL AND offering_completed_at IS NULL AND owner_burden_principal IS NULL)
        OR (completed_offering_id IS NOT NULL AND offering_completed_at IS NOT NULL
            AND owner_burden_payment_method IS NOT NULL
            AND owner_burden_principal IS NOT NULL AND owner_burden_principal >= 0)
    );
