-- 정산 서비스 "쓰기 부하테스트"(회차 개시)용 시드. asset_db에 자산·READY 수익·보유지분을 만든다.
-- docs/seed-settlement-performance.sql(읽기 전용, settlement_db)과는 목적·네임스페이스가 다르다 —
-- 이 스크립트는 'loadtest-' 접두사를 쓰고, 저쪽은 'seed-' 접두사를 쓰므로 서로 충돌하지 않고
-- 같은 환경에 동시에 넣어 둘 수 있다.
--
-- 실행 전 필수: settlement-service의 수익 폴링 스케줄러를 꺼 둘 것
--   (.env에 SETTLEMENT_REVENUE_POLLING_ENABLED=false 추가 후 재기동)
--   켜 둔 채로 실행하면 3분 폴러가 여기서 만든 READY 수익을 JMeter보다 먼저 가져가 버려서
--   POST /api/v1/settlements 가 전부 409(SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE)로 실패한다.
--
-- 실행:
-- docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--   psql -U moneytown -v investor_count=10000 -v asset_count=5 -d asset_db < docs/seed-settlement-load-assets.sql
--
-- asset_count는 "동시에 열 회차 수"와 같아야 한다 — 정산은 자산당 미완료 회차를 1개로 제한하므로
-- (uk_settlement_batches_asset_in_progress) 동시 요청은 서로 다른 자산이어야 막히지 않는다.
--
-- 생성 결과로 다음 JMeter CSV(assets.csv)를 만들어 쓴다 (investor_count=10000, asset_count=5 기준):
--   psql -U moneytown -d asset_db -c "
--     SELECT md5('loadtest-asset-' || n)::uuid AS asset_id,
--            md5('loadtest-revenue-' || n)::uuid AS revenue_id
--     FROM generate_series(1, 5) AS n" --csv > assets.csv

\set ON_ERROR_STOP on

\if :{?investor_count}
\else
\set investor_count 10000
\endif

\if :{?asset_count}
\else
\set asset_count 5
\endif

\connect asset_db

BEGIN;

-- 1) 자산 (자산당 지분 총량 = investor_count, 1인당 정확히 1주 → 단가 절사 차액이 0이 되게 맞춤)
--    valuation_amount = unit_price * total_share_quantity 를 정확히 나누어떨어지게 해서
--    ck_assets_rounding_difference 제약을 만족시킨다 (rounding_difference_amount = 0).
INSERT INTO p_assets (
    asset_id, user_id, asset_type, asset_name, owner_name, description,
    valuation_amount, expected_return_rate, detail_data,
    unit_price, total_share_quantity, rounding_difference_amount, allocated_quantity,
    asset_status, version,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('loadtest-asset-' || a)::uuid,
        md5('loadtest-issuer-' || a)::uuid,
        'REAL_ESTATE',
    'LoadTest Asset ' || a,
    'LoadTest Owner ' || a,
    '부하테스트용 시드 자산 (docs/seed-settlement-load-assets.sql)',
    10000::bigint * :investor_count::bigint,
        5.0000,
    '{}'::jsonb,
        10000,
    :investor_count::bigint,
        0,
    :investor_count::bigint,
        'APPROVED',
    0,
    now(), md5('loadtest-admin')::uuid,
        now(), md5('loadtest-admin')::uuid, false
FROM generate_series(1, :asset_count::integer) AS a
    ON CONFLICT (asset_id) DO NOTHING;

-- 2) 자산당 READY 수익 1건 (배당가능액 = investor_count * 10000원 → 최대 잔여법으로 1인당 정확히 10000원)
--    period_end를 "내일(KST)"로 잡는 이유: settlement의 보유지분 스냅샷 컷오프는
--    (asOf + 1일) 00:00 Asia/Seoul 이다 (HoldingQueryService.java:111-112).
--    아래에서 만드는 이력(history)의 created_at은 "지금"이므로, 컷오프가 반드시
--    "지금"보다 뒤에 오도록 period_end를 미래로 잡아야 방금 만든 지분이 스냅샷에 포함된다.
--    (recordDate를 과거로 잡으면 방금 만든 이력이 컷오프 밖으로 밀려나 조용히 제외된다.)
INSERT INTO p_revenues (
    revenue_id, asset_id, user_id, source_type, source_reference_id, revenue_type,
    gross_amount, expense_amount, fee_amount, currency,
    period_start, period_end, raw_payload, transfer_status,
    created_at, created_by, updated_at, updated_by
)
SELECT
    md5('loadtest-revenue-' || a)::uuid,
        md5('loadtest-asset-' || a)::uuid,
        md5('loadtest-issuer-' || a)::uuid,
        'SYNTHETIC',
    'loadtest-revenue-' || a,
    'RENTAL_INCOME',
    10000::numeric * :investor_count::numeric,
        0, 0, 'KRW',
    ((now() AT TIME ZONE 'Asia/Seoul')::date - INTERVAL '30 days')::date,
    ((now() AT TIME ZONE 'Asia/Seoul')::date + INTERVAL '1 day')::date,
    '{}'::jsonb,
    'READY',
    now(), md5('loadtest-admin')::uuid,
    now(), md5('loadtest-admin')::uuid
FROM generate_series(1, :asset_count::integer) AS a
ON CONFLICT (revenue_id) DO NOTHING;

-- 3) 보유지분: 자산당 investor_count명, 1인당 1주
INSERT INTO p_holdings (
    holding_id, asset_id, user_id, quantity, version,
    created_at, created_by, updated_at, updated_by
)
SELECT
    md5('loadtest-holding-' || a || '-' || i)::uuid,
        md5('loadtest-asset-' || a)::uuid,
        md5('loadtest-investor-' || i)::uuid,
        1, 0,
    now(), md5('loadtest-admin')::uuid,
        now(), md5('loadtest-admin')::uuid
FROM generate_series(1, :asset_count::integer) AS a
         CROSS JOIN generate_series(1, :investor_count::integer) AS i
    ON CONFLICT (asset_id, user_id) DO NOTHING;

-- 4) 배정 이력 (ALLOCATE) — 정산의 holdings 스냅샷 쿼리는 p_holdings.quantity가 아니라
--    이 이력을 컷오프 시점까지 재생(replay)해서 잔량을 계산한다. subscription_id는
--    (subscription_id, history_type) 부분 유니크 인덱스 대상이라 자산×투자자 조합마다 유일해야 한다.
INSERT INTO p_holding_histories (
    history_id, holding_id, subscription_id, history_type,
    quantity, balance_before, balance_after, idempotency_key, reason,
    created_at, created_by
)
SELECT
    md5('loadtest-history-' || a || '-' || i)::uuid,
        md5('loadtest-holding-' || a || '-' || i)::uuid,
        md5('loadtest-subscription-' || a || '-' || i)::uuid,
        'ALLOCATE',
    1, 0, 1,
    'loadtest-alloc-' || a || '-' || i,
    NULL,
    now(), md5('loadtest-admin')::uuid
FROM generate_series(1, :asset_count::integer) AS a
         CROSS JOIN generate_series(1, :investor_count::integer) AS i
    ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;

ANALYZE p_assets;
ANALYZE p_revenues;
ANALYZE p_holdings;
ANALYZE p_holding_histories;

-- 검증: 자산별 READY 수익 1건 + 보유지분 investor_count건이 만들어졌는지 확인
SELECT
    a.asset_name,
    a.asset_status,
    r.transfer_status,
    r.gross_amount,
    (SELECT count(*) FROM p_holdings h WHERE h.asset_id = a.asset_id) AS holding_count
FROM p_assets a
         JOIN p_revenues r ON r.asset_id = a.asset_id
WHERE a.asset_id IN (
    SELECT md5('loadtest-asset-' || n)::uuid
    FROM generate_series(1, :asset_count::integer) AS n
)
ORDER BY a.asset_name;