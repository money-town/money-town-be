-- 시나리오 E-1(RevenueReady 폴링 지연시간) 전용 — revenue 딱 1건만 만든다. asset_db만 건드린다.
--
-- 【왜 한 건씩 따로 실행하는가】
--   E-1이 증명할 명제는 "회차 개시까지의 지연이 폴링 3분 주기 안 어느 시점에 걸리느냐에 따라
--   0~180초 사이에서 랜덤하다"이다. revenue 20건을 한 번에(generate_series로) 만들면 전부
--   같은 순간에 READY가 되어 다음 폴링 사이클 한 번에 몰아서 잡히므로, "분포"가 아니라
--   "값 1개를 20번 복제"한 꼴이 된다. 그래서 이 스크립트는 asset_no 하나만 받아 revenue
--   1건을 만들고, 실행자가 실제 시계(wall-clock)로 시차를 두고 반복 호출해야 한다.
--
-- 【사전 조건】
--   docs/seed-settlement-scenario-e-assets.sql로 만든 자산(loadtest-e-asset-N)에만 실행할 것.
--   자산당 revenue는 딱 1건만 허용된다 — 두 번째 revenue를 열려면 첫 배치가 COMPLETED여야
--   하는데(uk_settlement_batches_asset_in_progress), 지갑 데이터가 없어 지급이 성공할 수
--   없으므로 COMPLETED가 되지 않는다. 즉 asset_no는 20건 모두 서로 달라야 한다.
--
-- 【T0 기록 방법】
--   이 스크립트가 끝에 방금 만든 revenue의 created_at을 그대로 출력한다 — 이 값이 T0다.
--   같은 asset_no로 다시 실행하면 ON CONFLICT DO NOTHING으로 아무 것도 안 만들어지고
--   "이전에 만든" created_at이 그대로 출력되니, 출력된 시각이 "방금"이 아니면 실수(중복 실행)를
--   의심할 것.
--
-- 실행 (매번 asset_no만 바꿔서, 실제 시간차를 두고 반복):
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -v asset_no=1 -d asset_db < docs/seed-settlement-scenario-e-revenue.sql
--
--   예: 0~180초 사이에 무작위로 흩어지도록, 20건을 3분 폴링 주기 여러 번에 걸쳐 나눠 실행
--   (예: 1건씩 임의 간격 0~9분으로 20회 반복하면 여러 폴링 사이클에 걸쳐 자연히 분산됨)

\set ON_ERROR_STOP on

\if :{?asset_no}
\else
\echo 'ERROR: -v asset_no=N 을 반드시 지정할 것 (docs/seed-settlement-scenario-e-assets.sql로 만든 자산 번호)'
\quit
\endif

\connect asset_db

-- gross_amount는 실제 이 자산의 holdings 수에 맞춰 계산한다(투자자 1인당 10,000원 배당).
-- seed-settlement-scenario-e-assets.sql 실행 시 넣은 investor_count 파라미터를 여기서 다시
-- 몰라도 되도록, 실제 저장된 holdings 행 수를 직접 세어 쓴다.
INSERT INTO p_revenues (
    revenue_id, asset_id, user_id, source_type, source_reference_id, revenue_type,
    gross_amount, expense_amount, fee_amount, currency,
    period_start, period_end, raw_payload, transfer_status,
    created_at, created_by, updated_at, updated_by
)
SELECT
    md5('loadtest-e-revenue-' || :asset_no::integer)::uuid,
    md5('loadtest-e-asset-' || :asset_no::integer)::uuid,
    md5('loadtest-e-issuer-' || :asset_no::integer)::uuid,
    'SYNTHETIC',
    'loadtest-e-revenue-' || :asset_no::integer,
    'RENTAL_INCOME',
    10000::numeric * (
        SELECT count(*) FROM p_holdings
        WHERE asset_id = md5('loadtest-e-asset-' || :asset_no::integer)::uuid
    ),
    0, 0, 'KRW',
    ((now() AT TIME ZONE 'Asia/Seoul')::date - INTERVAL '30 days')::date,
    ((now() AT TIME ZONE 'Asia/Seoul')::date + INTERVAL '1 day')::date,
    '{}'::jsonb,
    'READY',
    now(), md5('loadtest-e-admin')::uuid,
    now(), md5('loadtest-e-admin')::uuid
ON CONFLICT (revenue_id) DO NOTHING;

-- T0 — 이 값을 그대로 기록할 것 (같은 asset_no 재실행 시 "방금"이 아니면 중복 실행 실수)
SELECT
    :asset_no::integer AS asset_no,
    revenue_id,
    transfer_status,
    gross_amount,
    created_at AS t0
FROM p_revenues
WHERE revenue_id = md5('loadtest-e-revenue-' || :asset_no::integer)::uuid;