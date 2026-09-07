ALTER TABLE p_assets
    ADD COLUMN termination_requested_at TIMESTAMPTZ;

-- 기존 종료 요청·종료 자산은 마지막 변경 시각을 최초 값으로 보정한다.
UPDATE p_assets
SET termination_requested_at = updated_at
WHERE asset_status IN ('TERMINATION_REQUESTED', 'TERMINATED')
  AND termination_requested_at IS NULL;

ALTER TABLE p_assets
    ADD CONSTRAINT ck_assets_termination_requested_at
        CHECK (asset_status NOT IN ('TERMINATION_REQUESTED', 'TERMINATED')
            OR termination_requested_at IS NOT NULL);
