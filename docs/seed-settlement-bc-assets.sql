-- 정산 시나리오 B·C(지급 처리량 / 지갑 장애 전파) 전용 asset_db 시드.
-- 기존 seed-settlement-load-assets.sql(시나리오 A용)과 별개이며 재사용하지 않는다 — docs/star.md 6.3 참고.
--
-- 만드는 것 (자산 번호 a마다):
--   p_assets 1건(APPROVED) + READY 수익 1건 + 보유지분 N건(1인 1주) + ALLOCATE 이력 N건
--   ※ p_holding_subscription_states는 만들지 않는다 — 정산 스냅샷은 p_holding_histories만 재생한다
--     (HoldingQueryRepositoryImpl.findSnapshotByAssetId).
--
-- 파라미터 (psql -v 로 전달, 모두 선택):
--   prefix              UUID 접두사. 기본 bc. 기존 시드의 'loadtest-'와 다르게 유지할 것(키 충돌 방지)
--   asset_from,asset_to 자산 번호 범위(양끝 포함). 기본 101,101
--   investors_per_asset 자산당 투자자 수. 기본 1000
--   disjoint            0이면 모든 자산이 투자자 1..N을 공유(단일 자산·순차 실행용),
--                       1이면 자산 a가 서로 겹치지 않는 구간을 사용(다중 회차 동시 실행용):
--                         자산 asset_from → 투자자 1..N, 그다음 자산 → N+1..2N, ...
--                       기본 0
--
-- UUID 규칙 (wallet 시드·CSV와 반드시 동일해야 한다):
--   asset    md5(prefix || '-asset-'    || 자산번호)::uuid
--   revenue  md5(prefix || '-revenue-'  || 자산번호)::uuid
--   investor md5(prefix || '-investor-' || 투자자번호)::uuid   ← seed-settlement-bc-wallets.sql도 같은 식
--
-- 실행 예 (레포 루트, 자산 101 = 1,000명):
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -d asset_db -v investors_per_asset=1000 -v asset_from=101 -v asset_to=101 \
--     < docs/seed-settlement-bc-assets.sql
-- 다중 자산 8×1,250 (자산 121~128, 투자자 구간 분리):
--     -v investors_per_asset=1250 -v asset_from=121 -v asset_to=128 -v disjoint=1
--
-- 재실행 안전: 이미 있는 행은 건드리지 않는다(ON CONFLICT DO NOTHING). 단 같은 자산 번호를 다른
-- investors_per_asset로 다시 실행하면 조용히 섞이지 않도록 검증 단계에서 중단(ROLLBACK)한다.
--
-- 실행 전 필수: settlement-service의 수익 폴링을 꺼 둘 것(SETTLEMENT_REVENUE_POLLING_ENABLED=false).
--   켜 두면 폴러가 이 READY 수익을 JMeter보다 먼저 가져간다.

\set ON_ERROR_STOP on

\if :{?prefix}
\else
\set prefix bc
\endif
\if :{?asset_from}
\else
\set asset_from 101
\endif
\if :{?asset_to}
\else
\set asset_to :asset_from
\endif
\if :{?investors_per_asset}
\else
\set investors_per_asset 1000
\endif
\if :{?disjoint}
\else
\set disjoint 0
\endif

\connect asset_db

BEGIN;

-- 1) 자산. 1인 1주, 단가 10,000원 → 평가액 = 10,000 × N 으로 딱 나누어떨어져 절사 차액 0
--    (ck_assets_rounding_difference: unit_price = valuation/total_share, 차액 = 0).
INSERT INTO p_assets (
    asset_id, user_id, asset_type, asset_name, owner_name, description,
    valuation_amount, expected_return_rate, detail_data,
    unit_price, total_share_quantity, rounding_difference_amount, allocated_quantity,
    asset_status, version,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'prefix' || '-asset-' || a)::uuid,
    md5(:'prefix' || '-issuer-' || a)::uuid,
    'REAL_ESTATE',
    'BC LoadTest Asset ' || a,
    'BC LoadTest Owner ' || a,
    '정산 B·C 부하테스트용 시드 자산 (docs/seed-settlement-bc-assets.sql)',
    10000::bigint * :investors_per_asset::bigint,
    5.0000,
    '{}'::jsonb,
    10000,
    :investors_per_asset::bigint,
    0,
    :investors_per_asset::bigint,
    'APPROVED',
    0,
    now(), md5(:'prefix' || '-admin')::uuid,
    now(), md5(:'prefix' || '-admin')::uuid, false
FROM generate_series(:asset_from::integer, :asset_to::integer) AS a
ON CONFLICT DO NOTHING;

-- 2) 자산당 READY 수익 1건. 배당가능액 = N × 10,000원 → 1인당 정확히 10,000원.
--    period_end를 "내일(KST)"로 잡는 이유: 정산의 보유지분 스냅샷 컷오프는 (기준일 + 1일) 00:00 Asia/Seoul
--    (HoldingQueryService.getSnapshot)이고 기준일 기본값이 period_end다. 아래 이력의 created_at은 "지금"이라
--    컷오프가 반드시 그보다 뒤여야 방금 만든 지분이 스냅샷에 포함된다(과거로 잡으면 조용히 제외 → payout 0건).
INSERT INTO p_revenues (
    revenue_id, asset_id, user_id, source_type, source_reference_id, revenue_type,
    gross_amount, expense_amount, fee_amount, currency,
    period_start, period_end, raw_payload, transfer_status,
    created_at, created_by, updated_at, updated_by
)
SELECT
    md5(:'prefix' || '-revenue-' || a)::uuid,
    md5(:'prefix' || '-asset-' || a)::uuid,
    md5(:'prefix' || '-issuer-' || a)::uuid,
    'SYNTHETIC',
    :'prefix' || '-revenue-' || a,
    'RENTAL_INCOME',
    10000::numeric * :investors_per_asset::numeric,
    0, 0, 'KRW',
    ((now() AT TIME ZONE 'Asia/Seoul')::date - 30),
    ((now() AT TIME ZONE 'Asia/Seoul')::date + 1),
    '{}'::jsonb,
    'READY',
    now(), md5(:'prefix' || '-admin')::uuid,
    now(), md5(:'prefix' || '-admin')::uuid
FROM generate_series(:asset_from::integer, :asset_to::integer) AS a
ON CONFLICT DO NOTHING;

-- 3) 보유지분: 자산당 N명, 1인 1주. 투자자 번호 = (disjoint면 자산 순번 × N) + k
INSERT INTO p_holdings (
    holding_id, asset_id, user_id, quantity, version,
    created_at, created_by, updated_at, updated_by
)
SELECT
    md5(:'prefix' || '-holding-' || a || '-' || (off.v + k))::uuid,
    md5(:'prefix' || '-asset-' || a)::uuid,
    md5(:'prefix' || '-investor-' || (off.v + k))::uuid,
    1, 0,
    now(), md5(:'prefix' || '-admin')::uuid,
    now(), md5(:'prefix' || '-admin')::uuid
FROM generate_series(:asset_from::integer, :asset_to::integer) AS a
CROSS JOIN LATERAL (SELECT (a - :asset_from::integer) * :investors_per_asset::integer * :disjoint::integer AS v) AS off
CROSS JOIN generate_series(1, :investors_per_asset::integer) AS k
ON CONFLICT DO NOTHING;

-- 4) 배정 이력(ALLOCATE) — 정산 스냅샷은 p_holdings.quantity가 아니라 이 이력을 컷오프까지 재생해 잔량을 계산한다.
--    subscription_id는 (subscription_id, history_type) 부분 유니크 인덱스 대상이라 자산×투자자마다 유일해야 하고,
--    idempotency_key는 전역 유일(VARCHAR(100))이다. 둘 다 자산 번호와 투자자 번호를 모두 넣어 규모가 달라도 충돌하지 않게 한다.
INSERT INTO p_holding_histories (
    history_id, holding_id, subscription_id, history_type,
    quantity, balance_before, balance_after, idempotency_key, reason,
    created_at, created_by
)
SELECT
    md5(:'prefix' || '-history-' || a || '-' || (off.v + k))::uuid,
    md5(:'prefix' || '-holding-' || a || '-' || (off.v + k))::uuid,
    md5(:'prefix' || '-subscription-' || a || '-' || (off.v + k))::uuid,
    'ALLOCATE',
    1, 0, 1,
    :'prefix' || '-alloc-' || a || '-' || (off.v + k),
    NULL,
    now(), md5(:'prefix' || '-admin')::uuid
FROM generate_series(:asset_from::integer, :asset_to::integer) AS a
CROSS JOIN LATERAL (SELECT (a - :asset_from::integer) * :investors_per_asset::integer * :disjoint::integer AS v) AS off
CROSS JOIN generate_series(1, :investors_per_asset::integer) AS k
ON CONFLICT DO NOTHING;

-- 5) 검증: 범위 안 모든 자산의 보유지분·ALLOCATE 이력·수익 상태, 그리고 자산 지분 총량·배당가능액이 기대와 다르면
--    커밋하지 않고 중단한다. (같은 자산 번호를 다른 investors_per_asset로 재실행한 경우를 조용히 넘기지 않기 위함 —
--    투자자 수를 늘려 재실행하면 보유지분·이력은 새 행이 붙어 개수가 맞아 보이지만 자산·수익은 옛 값이 남으므로 값도 대조한다)
SELECT count(*) > 0 AS bad
FROM generate_series(:asset_from::integer, :asset_to::integer) AS a
WHERE (SELECT count(*) FROM p_holdings h
        WHERE h.asset_id = md5(:'prefix' || '-asset-' || a)::uuid) <> :investors_per_asset::integer
   OR (SELECT count(*) FROM p_holding_histories hh JOIN p_holdings h ON h.holding_id = hh.holding_id
        WHERE h.asset_id = md5(:'prefix' || '-asset-' || a)::uuid AND hh.history_type = 'ALLOCATE') <> :investors_per_asset::integer
   OR (SELECT count(*) FROM p_revenues r
        WHERE r.revenue_id = md5(:'prefix' || '-revenue-' || a)::uuid AND r.transfer_status = 'READY') <> 1
   -- ON CONFLICT DO NOTHING이라 자산·수익은 옛 값이 남는다. 보유지분/이력만 늘어난 채 통과하지 않도록 값 자체도 대조한다.
   -- 서브쿼리가 NULL(행 없음)일 수 있어 <>가 아니라 IS DISTINCT FROM.
   OR (SELECT total_share_quantity FROM p_assets
        WHERE asset_id = md5(:'prefix' || '-asset-' || a)::uuid) IS DISTINCT FROM :investors_per_asset::bigint
   OR (SELECT gross_amount FROM p_revenues
        WHERE revenue_id = md5(:'prefix' || '-revenue-' || a)::uuid) IS DISTINCT FROM 10000::numeric * :investors_per_asset::numeric
\gset

\if :bad
\echo '시드 검증 실패: 범위 안 자산의 보유지분/ALLOCATE 이력/READY 수익이 기대와 다릅니다. ROLLBACK 합니다.'
\echo '같은 자산 번호를 다른 investors_per_asset로 재실행했거나 이미 회차가 열린(TRANSFERRED) 자산일 수 있습니다.'
ROLLBACK;
-- \quit은 종료 코드를 지정하지 못한다. ON_ERROR_STOP 상태에서 일부러 에러를 내 psql이 0이 아닌 코드(3)로 끝나게 한다.
SELECT 1 / 0 AS seed_verification_failed;
\endif

COMMIT;

ANALYZE p_assets;
ANALYZE p_revenues;
ANALYZE p_holdings;
ANALYZE p_holding_histories;

-- 결과 요약 (자산별): holdings = allocate_histories = 투자자 수, 수익 READY, 배당가능액 = N × 10,000
SELECT
    a AS asset_no,
    (SELECT count(*) FROM p_holdings h WHERE h.asset_id = md5(:'prefix' || '-asset-' || a)::uuid) AS holdings,
    (SELECT count(*) FROM p_holding_histories hh JOIN p_holdings h ON h.holding_id = hh.holding_id
      WHERE h.asset_id = md5(:'prefix' || '-asset-' || a)::uuid AND hh.history_type = 'ALLOCATE') AS allocate_histories,
    r.transfer_status,
    r.gross_amount,
    r.period_end
FROM generate_series(:asset_from::integer, :asset_to::integer) AS a
JOIN p_revenues r ON r.revenue_id = md5(:'prefix' || '-revenue-' || a)::uuid
ORDER BY a;