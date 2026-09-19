-- T2(스케줄러 인덱스) 전후 비교 전용 시드. settlement_db만 건드린다.
--
-- 【왜 seed-settlement-scenario0.sql을 그대로 못 쓰는가】
--   scenario0 시드는 p_dividend_payouts.status를 전부 'PAID'로만 채운다(회차 수 효과만 보려고
--   일부러 그렇게 설계함). T2가 대상으로 하는 5개 쿼리는 status 기반 조회라 선택도가 분포에
--   좌우되는데, 전부 PAID면 PROCESSING/QUEUED/RETRYING용 partial 인덱스가 텅 비어 Before/After
--   둘 다 의미가 없어진다. 또한 p_final_settlement_payouts·p_final_settlement_batches는
--   지금까지 어떤 시드에도 없다 — 이 스크립트가 그 두 테이블을 처음 채운다.
--
-- 【분포 비율】 — 실제 운영 데이터(모수)가 없어 임의로 정함. 시장조사 불필요한 이유는
--   star.md 6장 논의 참고: T2가 보려는 건 "정확한 실제 비율"이 아니라 "이 비율에서 partial
--   인덱스가 실제로 쓰이는가·쓰기비용이 어느 방향으로 늘어나는가"이므로 비율 자체의 정확도는
--   결론에 영향을 주지 않는다. 아래에서 만드는 정확한 비율은 맨 아래 검증 쿼리로 확인한다.
--     · p_dividend_payouts        : 99%  PAID / 나머지 1%를 QUEUED·PROCESSING·RETRYING 균등 배분
--     · p_final_settlement_payouts: 위와 동일 비율
--     · p_final_settlement_batches: 대부분 status=COMPLETED & asset_termination_completed_at 존재,
--       0.2%만 asset_termination_completed_at이 NULL(= "종료 통보 대기 중", 6.1의 대상 쿼리가 찾는 행)
--
-- 【실행】
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -d settlement_db < docs/seed-settlement-t2.sql
--
--   ※ 실행마다 대상 테이블 5개를 TRUNCATE 하고 새로 채운다(누적되지 않음).
--   ※ p_settlement_batches/p_holdings_snapshots/p_dividend_payouts를 seed-settlement-scenario0.sql과
--     공유한다 — 같은 환경에서 시나리오 0을 다시 돌리려면 그 스크립트를 재실행해 덮어써야 한다.
--   ※ 필요시 investor_count / batches_per_investor / final_batch_count / final_investor_count를
--     -v 옵션으로 덮어쓸 수 있다(scenario0.sql과 동일한 방식).

\set ON_ERROR_STOP on

\if :{?investor_count}
\else
\set investor_count 10000
\endif

\if :{?batches_per_investor}
\else
\set batches_per_investor 100
\endif

\if :{?batch_count}
\else
\set batch_count 5000
\endif

\if :{?final_batch_count}
\else
\set final_batch_count 2000
\endif

\if :{?final_investor_count}
\else
\set final_investor_count 500
\endif

\connect settlement_db

TRUNCATE p_settlement_batches, p_holdings_snapshots, p_dividend_payouts,
         p_final_settlement_payouts, p_final_settlement_batches;

BEGIN;

-- =========================================================================
-- 1) 배당 정산 — p_settlement_batches / p_holdings_snapshots / p_dividend_payouts
--    배치·스냅샷 생성 로직은 seed-settlement-scenario0.sql과 동일(재현성 검증된 공식 그대로 재사용).
--    바뀌는 건 payout의 status뿐 — 전부 'PAID' 대신 (i+k)%300 기반 분포로 채운다.
-- =========================================================================

INSERT INTO p_settlement_batches (
    settlement_batch_id, asset_id, revenue_id, record_date,
    total_amount, status,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('t2-batch-' || a)::uuid,
        md5('t2-asset-' || (a % 200))::uuid,
        md5('t2-revenue-' || a)::uuid,
        (now() - ((:batch_count - a) || ' hours')::interval)::date,
        (:investor_count::bigint * :batches_per_investor::bigint / :batch_count::bigint) * 10000,
    'COMPLETED',
    now() - ((:batch_count - a) || ' hours')::interval, md5('t2-admin')::uuid,
        now() - ((:batch_count - a) || ' hours')::interval, md5('t2-admin')::uuid, false
FROM generate_series(1, :batch_count::integer) AS a;

INSERT INTO p_holdings_snapshots (
    holding_snapshot_id, settlement_batch_id, asset_id, snapshot_at,
    total_quantity, total_holders, total_share_quantity,
    created_at, created_by
)
SELECT
    md5('t2-snap-' || a)::uuid,
        md5('t2-batch-' || a)::uuid,
        md5('t2-asset-' || (a % 200))::uuid,
        (now() - ((:batch_count - a) || ' hours')::interval)::date,
        (:investor_count::bigint * :batches_per_investor::bigint / :batch_count::bigint),
    (:investor_count::integer * :batches_per_investor::integer / :batch_count::integer),
    (:investor_count::bigint * :batches_per_investor::bigint / :batch_count::bigint),
    now(), md5('t2-admin')::uuid
FROM generate_series(1, :batch_count::integer) AS a;

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
    md5('t2-payout-' || t.a || '-' || i)::uuid,
        md5('t2-batch-' || t.a)::uuid,
        md5('t2-investor-' || i)::uuid,
        round(1.0 / p.holders_per_batch, 8),
    10000,
    't2-' || t.a || '-' || i,
    -- 99% PAID / 나머지 1%를 QUEUED·PROCESSING·RETRYING에 균등 배분 (결정적 — random() 미사용)
    CASE (i + k) % 300
        WHEN 297 THEN 'QUEUED'
        WHEN 298 THEN 'PROCESSING'
        WHEN 299 THEN 'RETRYING'
        ELSE 'PAID'
    END,
    0,
    now() - ((:batch_count - t.a) || ' hours')::interval, md5('t2-admin')::uuid,
        now() - ((:batch_count - t.a) || ' hours')::interval, md5('t2-admin')::uuid,
        false
FROM params p
         CROSS JOIN generate_series(1, :investor_count::integer) AS i
         CROSS JOIN generate_series(0, :batches_per_investor::integer - 1) AS k
         CROSS JOIN LATERAL (SELECT k * p.stride + (i % p.stride) + 1 AS a) t;

-- =========================================================================
-- 2) 최종 정산 — p_final_settlement_batches / p_final_settlement_payouts
--    자산당 평생 1회이므로 "배치 수 = 자산 수"다. final_batch_count개의 자산을 만들고
--    각 자산마다 final_investor_count명의 투자자에게 1건씩 지급 행을 만든다.
-- =========================================================================

INSERT INTO p_final_settlement_batches (
    final_settlement_batch_id, asset_id, terminated_at, unit_price, total_amount, status,
    created_at, created_by, updated_at, updated_by, is_deleted, asset_termination_completed_at
)
SELECT
    md5('t2-final-batch-' || a)::uuid,
        md5('t2-final-asset-' || a)::uuid,
        now() - ((:final_batch_count - a) || ' hours')::interval,
    10000,
        :final_investor_count::bigint * 10000,
    -- 대부분 COMPLETED, 극소수만 DISBURSING/PARTIAL_FAILED (실제 쿼리 대상은 COMPLETED뿐)
    CASE
        WHEN a % 250 = 0 THEN 'PARTIAL_FAILED'
        WHEN a % 250 = 125 THEN 'DISBURSING'
        ELSE 'COMPLETED'
    END,
    now() - ((:final_batch_count - a) || ' hours')::interval, md5('t2-admin')::uuid,
        now() - ((:final_batch_count - a) || ' hours')::interval, md5('t2-admin')::uuid, false,
    -- 0.2%만 NULL로 남겨 "종료 통보 대기 중" 상태 재현 (6.1의 대상 쿼리가 찾는 행)
    -- ⚠️ 500의 배수는 전부 250의 배수이기도 해서 위 status CASE에서 PARTIAL_FAILED로 먼저 걸린다.
    --    나머지 0 대신 1을 써서 status가 COMPLETED로 떨어지는 a와 겹치게 한다(a%500=1 → a%250=1,
    --    250/125 어느 쪽도 아니므로 ELSE COMPLETED). 나머지 0을 쓰면 대상 쿼리가 0건만 찾는다.
    CASE
        WHEN a % 500 = 1 THEN NULL
        ELSE now() - ((:final_batch_count - a) || ' hours')::interval
    END
FROM generate_series(1, :final_batch_count::integer) AS a;

INSERT INTO p_final_settlement_payouts (
    final_settlement_payout_id, final_settlement_batch_id, investor_id,
    quantity, amount, idempotency_key, status, retry_count,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('t2-final-payout-' || a || '-' || i)::uuid,
        md5('t2-final-batch-' || a)::uuid,
        md5('t2-final-investor-' || i)::uuid,
        1,
    10000,
    't2-final-' || a || '-' || i,
    -- 배당 쪽과 동일한 99:1 분포 (a, i 조합 기준 — 배당의 (i,k)와 다른 조합이라 서로 독립적)
    CASE (a + i) % 300
        WHEN 297 THEN 'QUEUED'
        WHEN 298 THEN 'PROCESSING'
        WHEN 299 THEN 'RETRYING'
        ELSE 'PAID'
    END,
    0,
    now() - ((:final_batch_count - a) || ' hours')::interval, md5('t2-admin')::uuid,
        now() - ((:final_batch_count - a) || ' hours')::interval, md5('t2-admin')::uuid,
        false
FROM generate_series(1, :final_batch_count::integer) AS a
         CROSS JOIN generate_series(1, :final_investor_count::integer) AS i;

COMMIT;

-- ANALYZE만으로는 visibility map이 안 채워져 Index Only Scan이 Heap Fetches를 내고,
-- 그 heap 페이지가 EXPLAIN BUFFERS에 섞여 After의 "인덱스만으로 처리" 효과가 저평가된다.
-- VACUUM (ANALYZE)로 통계 갱신과 visibility map 채우기를 한 번에 한다.
VACUUM (ANALYZE) p_settlement_batches;
VACUUM (ANALYZE) p_holdings_snapshots;
VACUUM (ANALYZE) p_dividend_payouts;
VACUUM (ANALYZE) p_final_settlement_batches;
VACUUM (ANALYZE) p_final_settlement_payouts;

-- =========================================================================
-- 3) 검증 — 실제로 만들어진 분포와 행수를 눈으로 확인한다 (6.2에 이 결과를 그대로 기록할 것)
-- =========================================================================

SELECT 'p_dividend_payouts' AS table_name, status, count(*)
FROM p_dividend_payouts GROUP BY status ORDER BY status;

SELECT 'p_final_settlement_payouts' AS table_name, status, count(*)
FROM p_final_settlement_payouts GROUP BY status ORDER BY status;

SELECT
    count(*) AS total_batches,
    count(*) FILTER (WHERE status = 'COMPLETED')                                  AS completed,
    count(*) FILTER (WHERE status = 'COMPLETED' AND asset_termination_completed_at IS NULL)
                                                                                    AS pending_notify,
    pg_size_pretty(pg_relation_size('p_final_settlement_batches'))                 AS heap_size
FROM p_final_settlement_batches;

SELECT
    pg_size_pretty(pg_relation_size('p_dividend_payouts'))         AS dividend_payouts_heap,
    pg_size_pretty(pg_relation_size('p_final_settlement_payouts')) AS final_settlement_payouts_heap;