-- 시나리오 0(인덱스) 전용 시드. settlement_db만 건드린다.
--
-- 【목적】
--   "GET /api/v1/dividends/me 의 조회 비용이 '그 투자자의 배당 건수'가 아니라
--    '시스템 전체에 누적된 정산 회차 수'에 비례하는가" 를 측정한다.
--   따라서 회차 수(batch_count)만 변수로 두고, 나머지는 전부 고정한다.
--
-- 【고정되는 것】 — 회차 수를 바꿔도 아래 값은 변하지 않는다
--   · payout 총 행수      = investor_count × batches_per_investor = 1,000,000
--     → 테이블 크기(약 180MB) 고정 → shared_buffers(128MB) 대비 캐시 조건 동일
--   · 투자자 1명당 배당 건수 = batches_per_investor = 100
--     → "돌려받는 결과 집합 크기"가 동일하므로, 비용 차이는 순수하게 회차 수 탓
--
-- 【변하는 것】
--   · batch_count : 500 / 2000 / 5000 으로 바꿔가며 3회 측정
--   · 회차당 보유자 수 = investor_count × batches_per_investor / batch_count
--     (총 행수가 고정이라 회차가 늘면 회차당 보유자는 반비례로 줄어든다 — 실제와도 부합)
--
-- 【배정 규칙】 stride = batch_count / batches_per_investor
--   투자자 i 는 a = k*stride + (i % stride) + 1  (k = 0 .. batches_per_investor-1) 인 회차를 보유.
--   → 투자자마다 정확히 batches_per_investor 개의 서로 다른 회차를 갖고,
--     회차마다 정확히 investor_count/stride 명의 보유자를 갖는다.
--   ※ 기존 seed-settlement-performance.sql 은 단순 CROSS JOIN 이라
--     "모든 투자자가 모든 회차를 보유" → 투자자당 건수와 회차 수가 묶여버려 통제 실험이 불가능했다.
--
-- 【실행】 (회차 수만 바꿔가며 3회)
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -d settlement_db -v batch_count=500 < docs/seed-settlement-scenario0.sql
--
--   ※ 매 실행마다 기존 시드를 TRUNCATE 하고 새로 넣는다(누적되지 않음).
--   ※ batch_count 는 batches_per_investor(100)의 배수여야 한다 (500, 1000, 2000, 5000 ...).

\set ON_ERROR_STOP on

\if :{?batch_count}
\else
\set batch_count 5000
\endif

\if :{?investor_count}
\else
\set investor_count 10000
\endif

\if :{?batches_per_investor}
\else
\set batches_per_investor 100
\endif

\connect settlement_db

-- 회차 수를 바꿔가며 반복 측정하므로 항상 초기화 후 재적재한다.
TRUNCATE p_settlement_batches, p_holdings_snapshots, p_dividend_payouts;

BEGIN;

-- 1) 정산 회차 — batch_count 개.
--    회차마다 updated_at 을 1시간씩 어긋나게 둔다(회차 1이 가장 오래됨).
--    ORDER BY updated_at DESC 가 의미를 갖게 하기 위함이며, random() 을 쓰지 않아 재현 가능하다.
--    asset_id 는 200개 자산을 돌려 쓴다(한 자산에서 수익 이벤트가 반복 발생하는 실제 형태).
INSERT INTO p_settlement_batches (
    settlement_batch_id, asset_id, revenue_id, record_date,
    total_amount, status,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('s0-batch-' || a)::uuid,
        md5('s0-asset-' || (a % 200))::uuid,
        md5('s0-revenue-' || a)::uuid,
        (now() - ((:batch_count - a) || ' hours')::interval)::date,
        (:investor_count::bigint * :batches_per_investor::bigint / :batch_count::bigint) * 10000,
    'COMPLETED',
    now() - ((:batch_count - a) || ' hours')::interval, md5('s0-admin')::uuid,
        now() - ((:batch_count - a) || ' hours')::interval, md5('s0-admin')::uuid, false
FROM generate_series(1, :batch_count::integer) AS a;

-- 2) 회차별 보유지분 스냅샷 (UNIQUE(settlement_batch_id)) — 쿼리가 쓰지는 않지만 고아 데이터를 남기지 않기 위해 함께 적재
INSERT INTO p_holdings_snapshots (
    holding_snapshot_id, settlement_batch_id, asset_id, snapshot_at,
    total_quantity, total_holders, total_share_quantity,
    created_at, created_by
)
SELECT
    md5('s0-snap-' || a)::uuid,
        md5('s0-batch-' || a)::uuid,
        md5('s0-asset-' || (a % 200))::uuid,
        (now() - ((:batch_count - a) || ' hours')::interval)::date,
        (:investor_count::bigint * :batches_per_investor::bigint / :batch_count::bigint),
    (:investor_count::integer * :batches_per_investor::integer / :batch_count::integer),
    (:investor_count::bigint * :batches_per_investor::bigint / :batch_count::bigint),
    now(), md5('s0-admin')::uuid
FROM generate_series(1, :batch_count::integer) AS a;

-- 3) 배당 지급 내역 — 투자자 1명당 정확히 batches_per_investor 건.
--    payout 의 updated_at 은 소속 회차의 시각을 따른다(회차가 돌 때 지급되므로).
WITH params AS (
    SELECT (:batch_count::integer / :batches_per_investor::integer)                          AS stride,
           (:investor_count::numeric * :batches_per_investor::numeric / :batch_count::numeric) AS holders_per_batch
)
INSERT INTO p_dividend_payouts (
    dividend_payout_id, settlement_batch_id, investor_id,
    share_ratio, amount, idempotency_key, status, retry_count,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('s0-payout-' || t.a || '-' || i)::uuid,
        md5('s0-batch-' || t.a)::uuid,
        md5('s0-investor-' || i)::uuid,
        round(1.0 / p.holders_per_batch, 8),
    10000,
    's0-' || t.a || '-' || i,
    'PAID',
    0,
    now() - ((:batch_count - t.a) || ' hours')::interval, md5('s0-admin')::uuid,
        now() - ((:batch_count - t.a) || ' hours')::interval, md5('s0-admin')::uuid,
        false
FROM params p
         CROSS JOIN generate_series(1, :investor_count::integer) AS i
         CROSS JOIN generate_series(0, :batches_per_investor::integer - 1) AS k
         CROSS JOIN LATERAL (SELECT k * p.stride + (i % p.stride) + 1 AS a) t;

COMMIT;

ANALYZE p_settlement_batches;
ANALYZE p_holdings_snapshots;
ANALYZE p_dividend_payouts;

-- 4) 불변식 검증 — batch_count 를 바꿔도 payouts / payouts_per_investor / heap_size 는 동일해야 한다.
SELECT
    (SELECT count(*) FROM p_settlement_batches)                                                AS batches,
    (SELECT count(*) FROM p_dividend_payouts)                                                  AS payouts,
    (SELECT count(*) FROM p_dividend_payouts WHERE investor_id = md5('s0-investor-1')::uuid)   AS payouts_per_investor,
    (SELECT count(*) FROM p_dividend_payouts WHERE settlement_batch_id = md5('s0-batch-1')::uuid) AS holders_in_batch_1,
    pg_size_pretty(pg_relation_size('p_dividend_payouts'))                                     AS heap_size;