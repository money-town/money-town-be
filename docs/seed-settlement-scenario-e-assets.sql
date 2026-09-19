-- 시나리오 E(폴링→이벤트, 자산 종료 장애격리) 전용 시드 — 자산 + 보유지분만 만든다. asset_db만 건드린다.
--
-- 【시나리오 A/T15의 seed-settlement-load-assets.sql과 다른 점】
--   그 스크립트는 자산 생성과 동시에 REVENUE도 한 번에(generate_series로 전부 같은 순간) 만든다.
--   시나리오 E-1은 "폴링 3분 주기 안 어느 시점에 revenue가 READY 되느냐에 따라 지연이
--   0~180초 사이에서 랜덤하다"를 증명해야 하므로, revenue는 한 건씩 실제 시간차를 두고
--   따로 만들어야 한다(→ seed-settlement-scenario-e-revenue.sql, 매번 asset_no 하나만 지정해 실행).
--   그래서 이 스크립트는 revenue를 아예 만들지 않는다 — 자산과 holdings만 미리 준비해둔다.
--
-- 【E-2(자산 종료 장애격리)에도 그대로 쓴다】
--   AssetCommandService.prepareAssetTermination → settlement의 openFinalSettlement는
--   revenue와 무관하게 동작하고, holdings만 있으면 된다(holders가 0명이면
--   FINAL_SETTLEMENT_HOLDERS_NOT_FOUND). 그래서 이 스크립트로 만든 자산을 E-1/E-2 양쪽에 나눠 쓴다.
--
-- 【네임스페이스】 'loadtest-e-' 접두사 — 시나리오 A/T15가 이미 소진한 'loadtest-asset-1~10'과
--   겹치지 않도록 별도 번호 체계를 쓴다(1번부터 다시 시작해도 asset_id가 달라 충돌하지 않음).
--
-- 【투자자 수를 일부러 작게 잡은 이유】
--   이 시나리오는 폴링 지연시간과 장애격리를 보는 것이지 커넥션 풀/처리량이 대상이 아니다.
--   investor_count를 10,000으로 잡으면 holdings 스냅샷 페이징(100건씩)이 100회 왕복하며
--   E-2의 "자산 종료 요청 응답시간"에 불필요한 지연을 더한다. 기본값을 100으로 낮춰
--   측정하려는 것(폴링 지연/장애격리) 외의 변수를 최소화한다. 필요하면 override 가능.
--
-- 실행 전 필수: 시나리오 A/T15와 달리 이번엔 수익 폴링 스케줄러가 켜져 있어야 한다
--   (.env의 SETTLEMENT_REVENUE_POLLING_ENABLED=true 확인 — E-1의 측정 대상 자체가 이 폴러다)
--
-- 실행 (asset_count만큼 1번부터 새로 만듦 — 이미 있으면 ON CONFLICT DO NOTHING으로 건너뜀):
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -v investor_count=100 -v asset_count=25 -d asset_db < docs/seed-settlement-scenario-e-assets.sql
--
-- asset_count 산정: E-1 20건(자산 1개당 revenue 1건, 재사용 불가) + E-2 Before/After 등 여유분 5건 = 25

\set ON_ERROR_STOP on

\if :{?investor_count}
\else
\set investor_count 100
\endif

\if :{?asset_count}
\else
\set asset_count 25
\endif

\connect asset_db

BEGIN;

-- 1) 자산 (자산당 지분 총량 = investor_count, 1인당 정확히 1주 → 단가 절사 차액 0)
INSERT INTO p_assets (
    asset_id, user_id, asset_type, asset_name, owner_name, description,
    valuation_amount, expected_return_rate, detail_data,
    unit_price, total_share_quantity, rounding_difference_amount, allocated_quantity,
    asset_status, version,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5('loadtest-e-asset-' || a)::uuid,
    md5('loadtest-e-issuer-' || a)::uuid,
    'REAL_ESTATE',
    'LoadTest-E Asset ' || a,
    'LoadTest-E Owner ' || a,
    '시나리오 E(폴링/장애격리) 전용 시드 자산 (docs/seed-settlement-scenario-e-assets.sql)',
    10000::bigint * :investor_count::bigint,
    5.0000,
    '{}'::jsonb,
    10000,
    :investor_count::bigint,
    0,
    :investor_count::bigint,
    'APPROVED',
    0,
    now(), md5('loadtest-e-admin')::uuid,
    now(), md5('loadtest-e-admin')::uuid, false
FROM generate_series(1, :asset_count::integer) AS a
ON CONFLICT (asset_id) DO NOTHING;

-- 2) 보유지분: 자산당 investor_count명, 1인당 1주
INSERT INTO p_holdings (
    holding_id, asset_id, user_id, quantity, version,
    created_at, created_by, updated_at, updated_by
)
SELECT
    md5('loadtest-e-holding-' || a || '-' || i)::uuid,
    md5('loadtest-e-asset-' || a)::uuid,
    md5('loadtest-e-investor-' || i)::uuid,
    1, 0,
    now(), md5('loadtest-e-admin')::uuid,
    now(), md5('loadtest-e-admin')::uuid
FROM generate_series(1, :asset_count::integer) AS a
CROSS JOIN generate_series(1, :investor_count::integer) AS i
ON CONFLICT (asset_id, user_id) DO NOTHING;

-- 3) 배정 이력 — 정산의 holdings 스냅샷 쿼리가 p_holdings.quantity가 아니라 이 이력을
--    컷오프 시점까지 재생(replay)해서 잔량을 계산하므로 반드시 함께 있어야 한다.
INSERT INTO p_holding_histories (
    history_id, holding_id, subscription_id, history_type,
    quantity, balance_before, balance_after, idempotency_key, reason,
    created_at, created_by
)
SELECT
    md5('loadtest-e-history-' || a || '-' || i)::uuid,
    md5('loadtest-e-holding-' || a || '-' || i)::uuid,
    md5('loadtest-e-subscription-' || a || '-' || i)::uuid,
    'ALLOCATE',
    1, 0, 1,
    'loadtest-e-alloc-' || a || '-' || i,
    NULL,
    now(), md5('loadtest-e-admin')::uuid
FROM generate_series(1, :asset_count::integer) AS a
CROSS JOIN generate_series(1, :investor_count::integer) AS i
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;

ANALYZE p_assets;
ANALYZE p_holdings;
ANALYZE p_holding_histories;

-- 검증: 자산 asset_count개가 APPROVED 상태로, investor_count명의 보유지분과 함께 만들어졌는지 확인
SELECT
    a.asset_name,
    a.asset_status,
    (SELECT count(*) FROM p_holdings h WHERE h.asset_id = a.asset_id) AS holding_count
FROM p_assets a
WHERE a.asset_id IN (
    SELECT md5('loadtest-e-asset-' || n)::uuid
    FROM generate_series(1, :asset_count::integer) AS n
)
ORDER BY a.asset_name;

-- 다음 단계:
--   E-1: docs/seed-settlement-scenario-e-revenue.sql 를 asset_no 하나씩 지정해 시차를 두고 반복 실행
--   E-2: 위에서 만든 자산 중 아직 revenue/termination을 안 쓴 번호로 바로
--        POST /api/v1/assets/{assetId}/termination-requests 호출 (revenue 불필요)