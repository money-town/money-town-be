-- 서로 다른 revenueId로 같은 assetId에 대해 동시에 openBatch()가 호출되면, 애플리케이션의
-- existsBy... 체크(락 없는 SELECT)만으로는 두 요청이 모두 통과해 진행 중인 배치가 중복
-- 생성될 수 있다(TOCTOU). 자산별로 완료되지 않은 배치가 하나만 존재하도록 DB 레벨에서 강제한다.
CREATE UNIQUE INDEX uk_settlement_batches_asset_in_progress
    ON p_settlement_batches (asset_id)
    WHERE is_deleted = false AND status <> 'COMPLETED';