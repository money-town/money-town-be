-- 정산 시나리오 B·C 전용 wallet_db 시드. 투자자 지갑을 1..investor_count 번까지 만든다.
-- 지갑은 투자자 단위(자산 무관)라 한 번만 만들면 모든 회차가 공유한다 — docs/star.md 6.3 계약 3.
--
-- 없으면 지급이 전부 WALLET_NOT_FOUND로 즉시 실패해 처리량이 아니라 "실패 속도"를 재게 된다.
--
-- UUID 규칙은 seed-settlement-bc-assets.sql과 글자 하나까지 같아야 한다:
--   investor md5(prefix || '-investor-' || 투자자번호)::uuid
--
-- 파라미터 (psql -v, 모두 선택):
--   prefix          기본 bc  (assets 시드와 반드시 동일)
--   investor_count  기본 10000 (다중 자산 8×1,250 = 10,000 까지 커버)
--
-- 실행 (레포 루트):
--   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U moneytown -d wallet_db < docs/seed-settlement-bc-wallets.sql
--
-- 재실행 안전: 이미 있는 지갑은 건드리지 않는다(uk_wallets_user_id ON CONFLICT DO NOTHING).
-- balance는 0으로 시작한다(배당 입금 후 잔액이 늘어남 → 회차 후 "PAID 건수 × 10,000 = 잔액 증가분"으로 대조 가능).

\set ON_ERROR_STOP on

\if :{?prefix}
\else
\set prefix bc
\endif
\if :{?investor_count}
\else
\set investor_count 10000
\endif

\connect wallet_db

BEGIN;

INSERT INTO p_wallets (user_id, balance, hold_balance, available_balance, created_by, updated_by)
SELECT
    md5(:'prefix' || '-investor-' || i)::uuid,
    0, 0, 0,
    md5(:'prefix' || '-admin')::uuid,
    md5(:'prefix' || '-admin')::uuid
FROM generate_series(1, :investor_count::integer) AS i
ON CONFLICT (user_id) DO NOTHING;

-- 검증: 1..investor_count 전원의 지갑이 있어야 한다
SELECT count(*) <> :investor_count::integer AS bad
FROM p_wallets
WHERE user_id IN (SELECT md5(:'prefix' || '-investor-' || i)::uuid FROM generate_series(1, :investor_count::integer) AS i)
\gset

\if :bad
\echo '시드 검증 실패: 투자자 지갑 수가 investor_count와 다릅니다. ROLLBACK 합니다.'
ROLLBACK;
-- \quit은 종료 코드를 지정하지 못한다. ON_ERROR_STOP 상태에서 일부러 에러를 내 psql이 0이 아닌 코드(3)로 끝나게 한다.
SELECT 1 / 0 AS seed_verification_failed;
\endif

COMMIT;

ANALYZE p_wallets;

SELECT count(*) AS wallets_created
FROM p_wallets
WHERE user_id IN (SELECT md5(:'prefix' || '-investor-' || i)::uuid FROM generate_series(1, :investor_count::integer) AS i);