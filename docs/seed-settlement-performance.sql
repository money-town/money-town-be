-- 정산 서비스 로컬 부하테스트용 시드. settlement_db만 건드린다.
-- 실행: docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--   psql -U moneytown -v investor_count=100 -v asset_count=2 -d settlement_db < docs/seed-settlement-performance.sql
\set ON_ERROR_STOP on

\if :{?investor_count}
\else
\set investor_count 100
\endif

\if :{?asset_count}
\else
\set asset_count 2
\endif

\connect settlement_db

BEGIN;

-- 자산별로 완료된 정산 배치 하나씩 생성 (배당 100% 분배, 최대 잔여법으로 잔여 없이 전액 소진)
INSERT INTO p_settlement_batches (
    settlement_batch_id, asset_id, revenue_id, record_date,
    total_amount, status,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('seed-settlement-batch-' || n)::uuid,
    md5('seed-asset-' || n)::uuid,
    md5('seed-revenue-' || n)::uuid,
    CURRENT_DATE,
    :investor_count::bigint * 10000,
    'COMPLETED',
    now(), md5('seed-admin')::uuid,
    now(), md5('seed-admin')::uuid, false
FROM generate_series(1, :asset_count::integer) AS n
ON CONFLICT (settlement_batch_id) DO NOTHING;

-- 배치당 홀딩 스냅샷 1건 (UNIQUE(settlement_batch_id))
INSERT INTO p_holdings_snapshots (
    holding_snapshot_id, settlement_batch_id, asset_id, snapshot_at,
    total_quantity, total_holders, total_share_quantity,
    created_at, created_by
)
SELECT
    md5('seed-holding-snapshot-' || n)::uuid,
    md5('seed-settlement-batch-' || n)::uuid,
    md5('seed-asset-' || n)::uuid,
    CURRENT_DATE,
    :investor_count::bigint, :investor_count::integer, :investor_count::bigint,
    now(), md5('seed-admin')::uuid
FROM generate_series(1, :asset_count::integer) AS n
ON CONFLICT (settlement_batch_id) DO NOTHING;

-- 투자자 × 자산 조합으로 지급 완료(PAID) 내역 생성 → 투자자 1명당 asset_count건씩 조회됨
INSERT INTO p_dividend_payouts (
    dividend_payout_id, settlement_batch_id, investor_id,
    share_ratio, amount, idempotency_key, status, retry_count,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('seed-dividend-payout-' || a || '-' || i)::uuid,
    md5('seed-settlement-batch-' || a)::uuid,
    md5('seed-investor-' || i)::uuid,
    (1.0 / :investor_count::numeric),
    10000, 'seed-dividend-' || a || '-' || i, 'PAID', 0,
    now(), md5('seed-admin')::uuid,
    now(), md5('seed-admin')::uuid, false
FROM generate_series(1, :asset_count::integer) AS a
CROSS JOIN generate_series(1, :investor_count::integer) AS i
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
ANALYZE p_settlement_batches;
ANALYZE p_holdings_snapshots;
ANALYZE p_dividend_payouts;

\echo 'Settlement seed completed.'