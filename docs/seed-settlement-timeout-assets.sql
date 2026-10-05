-- 목적: 정산 저장 트랜잭션 타임아웃(20초) 재측정 실험에서 POST /api/v1/settlements를
--       반복 트리거하기 위한 자산 + READY 수익 + 보유지분(holdings) 시딩.
--
-- docs/seed-settlement-index-cost.sql과는 완전히 별개다 — 그 스크립트는 settlement_db에
-- SQL로 직접 INSERT해 애플리케이션 로직(Feign 호출)을 건너뛰지만, 이번 실험은
-- POST /settlements가 asset-service에 실제로 왕복(fetchAndValidateRevenue,
-- fetchAndValidateHoldingsSnapshot)하므로 asset_db에 "API가 읽을 수 있는" 정합적인
-- 데이터가 있어야 한다. 그래서 asset_db에서 실행한다.
--
-- 같은 자산·수익으로는 회차를 다시 열 수 없다(uk_settlement_batches_revenue_id,
-- uk_settlement_batches_asset_in_progress — asset당 FAILED/PARTIAL_FAILED/DISBURSING 등
-- "진행 중" 배치가 있으면 새 배치를 못 연다, V17). 그래서 반복 횟수만큼 자산을 "재사용"하지 않고
-- asset_count개를 미리 만들어 1회씩만 쓴다.
--
-- 사용법 (asset_count·investor_count 둘 다 필수 — 기본값을 스크립트에 넣지 않는다,
-- 다른 시드 스크립트(seed-settlement-scenario0.sql)와 같은 관례):
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -d asset_db -v asset_count=25 -v investor_count=10000 -f - \
--     < docs/seed-settlement-timeout-assets.sql
--
-- asset_no를 키로 쓰는 결정적 UUID(md5(...))라 asset_count를 늘려 재실행해도 기존 자산은
-- 안 건드리고 새 번호만 추가된다(ON CONFLICT DO NOTHING) — seed-settlement-load-assets.sql과
-- 같은 패턴.

-- ============================================================
-- 0) 자산 asset_count개 — 전부 완전 배정(allocated_quantity = total_share_quantity),
--    unit_price·valuation_amount·total_share_quantity를 ck_assets_rounding_difference가
--    0으로 나오게 고정값으로 잡았다 (10,000원 × 1,000만 주 = 1,000억, 반올림 차액 0).
-- ============================================================
INSERT INTO p_assets (
    asset_id, user_id, asset_type, asset_name, owner_name, description,
    valuation_amount, expected_return_rate, unit_price, total_share_quantity,
    rounding_difference_amount, allocated_quantity, asset_status,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('timeout-seed-asset-' || n)::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid,
    'REAL_ESTATE',
    'timeout-seed-asset-' || n,
    'timeout-seed-owner',
    'persist() 트랜잭션 타임아웃 재측정용 더미 자산',
    100000000000,
    0.05,
    10000,
    10000000,
    0,
    10000000,
    'APPROVED',
    now() - interval '60 days', '00000000-0000-0000-0000-000000000000'::uuid,
    now() - interval '60 days', '00000000-0000-0000-0000-000000000000'::uuid,
    false
FROM generate_series(1, :asset_count) AS n
ON CONFLICT (asset_id) DO NOTHING;

-- ============================================================
-- 1) 자산별 holdings investor_count명, 1인당 1,000주
--    (investor_count=10,000이면 총 10,000,000주 = total_share_quantity와 정확히 일치)
-- ============================================================
INSERT INTO p_holdings (holding_id, asset_id, user_id, quantity, created_at, created_by, updated_at, updated_by)
SELECT
    md5('timeout-seed-holding-' || a.n || '-' || i)::uuid,
    md5('timeout-seed-asset-' || a.n)::uuid,
    md5('timeout-seed-investor-' || a.n || '-' || i)::uuid,
    1000,
    now() - interval '60 days', '00000000-0000-0000-0000-000000000000'::uuid,
    now() - interval '60 days', '00000000-0000-0000-0000-000000000000'::uuid
FROM generate_series(1, :asset_count) AS a(n)
CROSS JOIN generate_series(1, :investor_count) AS i
ON CONFLICT (asset_id, user_id) DO NOTHING;

-- ============================================================
-- 2) holdings 스냅샷은 p_holdings.quantity가 아니라 p_holding_histories의
--    ALLOCATE/REVOKE/ADJUSTMENT 합계로 계산된다 (HoldingQueryRepositoryImpl.snapshotBalance()).
--    이 이력이 없으면 getHoldingsSnapshot이 투자자 수량을 전부 0으로 돌려주고,
--    배당 분배 계산(DividendDistributionCalculator)이 totalHoldingQuantity=0으로
--    실패하거나 전원 0원 배분이 된다 — 반드시 같이 넣어야 한다.
-- ============================================================
INSERT INTO p_holding_histories (
    history_id, holding_id, subscription_id, history_type, quantity,
    balance_before, balance_after, idempotency_key, created_at, created_by
)
SELECT
    md5('timeout-seed-history-' || a.n || '-' || i)::uuid,
    md5('timeout-seed-holding-' || a.n || '-' || i)::uuid,
    gen_random_uuid(),
    'ALLOCATE',
    1000,
    0,
    1000,
    'timeout-seed-' || a.n || '-' || i,
    now() - interval '60 days',
    '00000000-0000-0000-0000-000000000000'::uuid
FROM generate_series(1, :asset_count) AS a(n)
CROSS JOIN generate_series(1, :investor_count) AS i
ON CONFLICT (idempotency_key) DO NOTHING;

-- ============================================================
-- 3) 자산별 READY 수익 1건 — 배당 가능 금액 5억(gross 500,000,000, expense/fee 0)
--    period_end를 과거(1일 전)로 둬서, 위 holdings 이력(60일 전)이 스냅샷 컷오프
--    (period_end + 1일) 안에 확실히 들어오게 했다.
-- ============================================================
INSERT INTO p_revenues (
    revenue_id, asset_id, user_id, source_type, source_reference_id, revenue_type,
    gross_amount, expense_amount, fee_amount, currency, period_start, period_end,
    transfer_status, created_at, created_by, updated_at, updated_by
)
SELECT
    md5('timeout-seed-revenue-' || n)::uuid,
    md5('timeout-seed-asset-' || n)::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid,
    'ADMIN',
    'timeout-seed-revenue-' || n,
    'RENTAL_INCOME',
    500000000, 0, 0, 'KRW',
    (now() - interval '30 days')::date,
    (now() - interval '1 days')::date,
    'READY',
    now() - interval '30 days', '00000000-0000-0000-0000-000000000000'::uuid,
    now() - interval '30 days', '00000000-0000-0000-0000-000000000000'::uuid
FROM generate_series(1, :asset_count) AS n
ON CONFLICT (asset_id, source_type, source_reference_id) DO NOTHING;

-- ============================================================
-- 4) 트리거용 (assetId, revenueId) 목록 — POST /settlements curl에 순서대로 그대로 쓴다.
--    ⚠ 이 수익들은 transfer_status='READY'라 SETTLEMENT_REVENUE_POLLING_ENABLED=true면
--    RevenuePollingScheduler가 먼저 채간다 — 반드시 false로 끈 뒤 시딩·트리거할 것.
-- ============================================================
SELECT
    md5('timeout-seed-asset-' || n)::uuid AS asset_id,
    md5('timeout-seed-revenue-' || n)::uuid AS revenue_id
FROM generate_series(1, :asset_count) AS n
ORDER BY n;