-- 목적: idx_dividend_payouts_investor(V15) 유무에 따른
--       "회차 개시 시 payout 대량 INSERT" 비용(WAL bytes/records) 비교.
-- docs/localTest.md "시나리오 0 — 부수 측정" 표의 빈 칸을 채우기 위한 1회성 실험 스크립트.
--
-- 절차 (자세한 이유는 채팅 답변 참고):
--   1) idx_dividend_payouts_investor 있으면 DROP (인덱스 없는 상태 만들기)
--   2) 이 파일의 0)·1) 섹션을 psql -f 로 1회 실행 (배경 데이터 20만 행 + FK용 더미 회차)
--   3) 이 파일의 2) 섹션만 10회 반복 실행하며 매번 WAL bytes 기록 → "Before" 평균
--   4) CREATE INDEX idx_dividend_payouts_investor ... (V15와 동일 정의, 맨 아래 참고)
--   5) 2) 섹션 10회 반복 재실행 → "After" 평균
--   6) Before/After WAL bytes 비교해 docs/localTest.md 표에 채워넣기

-- ============================================================
-- 0) FK(settlement_batch_id)를 만족시키기 위한 더미 회차 1건 — 최초 1회만 실행
-- ============================================================
INSERT INTO p_settlement_batches (
    settlement_batch_id, asset_id, revenue_id, record_date, total_amount, status,
    created_at, created_by, updated_at, updated_by, is_deleted
) VALUES (
    '00000000-0000-0000-0000-0000000000b1'::uuid,
    gen_random_uuid(),
    gen_random_uuid(),
    now(), 1000000000, 'COMPLETED',
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    false
)
ON CONFLICT (settlement_batch_id) DO NOTHING;

-- ============================================================
-- 1) 배경 데이터 20만 행 — 최초 1회만 실행 (재실행하면 20만 행이 또 쌓이니 주의)
--    investor_id를 gen_random_uuid()로 완전 무작위 생성해, 실제 회차처럼
--    "키 순서가 무작위인" 배경 테이블을 만든다 (순차 삽입이면 B-tree에 유리하게 왜곡됨).
--    ⚠ uk_dividend_payouts_batch_investor UNIQUE(settlement_batch_id, investor_id) 때문에
--       investor_id를 소수(5,000개 등)로 제한하면 같은 더미 회차 안에서 반드시 충돌한다.
--       gen_random_uuid()는 128비트 난수라 이 규모에서는 충돌 확률이 사실상 0이다.
-- ============================================================
INSERT INTO p_dividend_payouts (
    dividend_payout_id, settlement_batch_id, investor_id, share_ratio, amount,
    idempotency_key, status, retry_count, created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    gen_random_uuid(),
    '00000000-0000-0000-0000-0000000000b1'::uuid,
    gen_random_uuid(),
    0.00012345,
    1000,
    'idx-cost-baseline-' || gen_random_uuid(),
    'PAID',
    0,
    now() - (random() * interval '30 days'),
    '00000000-0000-0000-0000-000000000000'::uuid,
    now() - (random() * interval '30 days'),
    '00000000-0000-0000-0000-000000000000'::uuid,
    false
FROM generate_series(1, 200000);

-- ============================================================
-- 2) 벤치 대상 — 이 블록만 따로 10회 반복 실행 (Before 10회 / After 10회, 총 20회)
--    실제 회차 개시 1건이 만드는 payout 배치(약 1만 건)를 흉내낸다.
--    10,000명의 서로 다른(무작위 순서) investor에게 지급이 나가는 상황과 같다.
-- ============================================================
BEGIN;
EXPLAIN (ANALYZE, BUFFERS, WAL)
INSERT INTO p_dividend_payouts (
    dividend_payout_id, settlement_batch_id, investor_id, share_ratio, amount,
    idempotency_key, status, retry_count, created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    gen_random_uuid(),
    '00000000-0000-0000-0000-0000000000b1'::uuid,
    gen_random_uuid(),
    round(random()::numeric, 8),
    (random() * 100000)::bigint,
    'idx-cost-bench-' || gen_random_uuid(),
    'QUEUED',
    0,
    now(),
    '00000000-0000-0000-0000-000000000000'::uuid,
    now(),
    '00000000-0000-0000-0000-000000000000'::uuid,
    false
FROM generate_series(1, 10000);
ROLLBACK;

-- ============================================================
-- 참고: V15와 동일한 인덱스 정의 (4단계에서 CREATE할 때 이걸 그대로 쓸 것)
-- ============================================================
-- CREATE INDEX IF NOT EXISTS idx_dividend_payouts_investor
--     ON p_dividend_payouts (investor_id, updated_at DESC, dividend_payout_id ASC)
--     WHERE is_deleted = false;