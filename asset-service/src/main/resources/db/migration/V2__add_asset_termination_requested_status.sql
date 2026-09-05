-- 자산 종료 요청 상태를 DB 상태 제약조건에 추가한다.

ALTER TABLE p_assets
    DROP CONSTRAINT p_assets_asset_status_check;

ALTER TABLE p_assets
    ADD CONSTRAINT ck_assets_status
        CHECK (asset_status IN (
            'DRAFT',
            'REVIEW_REQUESTED',
            'APPROVED',
            'REJECTED',
            'SUSPENDED',
            'TERMINATION_REQUESTED',
            'TERMINATED'
        ));
