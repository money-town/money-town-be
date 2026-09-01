-- 자산 서비스의 수익 조회(GET .../revenues/{revenueId})와 보유지분 스냅샷 조회
-- (GET .../holdings?asOf=)가 배당 기준일을 날짜(DATE) 단위로만 주고받기로 확정되어,
-- 그동안 TIMESTAMPTZ로 잡혀 있던 배당 기준일 컬럼을 DATE로 맞춘다.
ALTER TABLE p_settlement_batches
    ALTER COLUMN record_date TYPE DATE USING record_date::date;

ALTER TABLE p_holdings_snapshots
    ALTER COLUMN snapshot_at TYPE DATE USING snapshot_at::date;