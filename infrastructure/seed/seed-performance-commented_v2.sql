-- 성능 테스트용 선행 데이터를 생성하는 PostgreSQL psql 스크립트입니다.
-- 파일명: seed-performance-commented.sql
-- 대상: user_db, wallet_db, asset_db, offering_db
-- 청약 API를 실제로 호출하기 직전 상태까지 만들며, 청약 데이터 자체는 생성하지 않습니다.
--
-- UUID는 md5('실행 식별값-고정 접두사-' || 순번)를 UUID로 변환하여 생성합니다.
-- seed_namespace와 seed_batch를 바꾸면 기존 데이터와 충돌하지 않는 새 테스트 세트를 만들 수 있습니다.
-- 같은 옵션으로 다시 실행하면 같은 UUID가 만들어지고, ON CONFLICT로 중복 입력을 피합니다.
-- 이미 부하 테스트에 사용한 동일 namespace 데이터의 지갑 잔액과 공모 수량은 복구하지 않습니다.
-- p_subscriptions에는 (offering_id, user_id) UNIQUE 제약이 있고 Pre-FDS도 사용자별로 집계하므로
-- JMeter는 각 청약 요청에 서로 다른 투자자 계정을 사용해야 합니다.

-- SQL 하나라도 실패하면 이후 DB 시딩을 중단합니다.
\set ON_ERROR_STOP on

-- 명령행에서 -v 옵션을 전달하지 않았을 때 사용할 단계별 부하 테스트 기본값입니다.
\if :{?investor_count}
\else
-- 1/10/100/1000 threads를 각각 loop 10으로 실행하면 총 11,110건입니다.
-- 요청마다 고유 투자자를 사용하고 약 8%의 여유 계정을 확보합니다.
\set investor_count 12000
\endif

\if :{?issuer_count}
\else
\set issuer_count 1
\endif

\if :{?offering_count}
\else
-- 하나의 공모에 요청을 집중하여 remaining_quantity 조건부 UPDATE 경합을 측정합니다.
\set offering_count 1
\endif

\if :{?offering_total_quantity}
\else
-- API 부하 측정 중 조기 매진되지 않도록 총 11,110건보다 여유 있게 설정합니다.
-- 전체 Saga 완료를 확인할 때는 실제 성공 요청 수와 같은 값(예: 11110)을 전달합니다.
\set offering_total_quantity 12000
\endif

\if :{?price_per_unit}
\else
\set price_per_unit 10000
\endif

\if :{?max_subscription_quantity}
\else
\set max_subscription_quantity 100
\endif

\if :{?wallet_balance}
\else
\set wallet_balance 1000000
\endif

\if :{?seed_namespace}
\else
-- 재측정 시 UUID, 이메일과 원장 멱등 키를 새로 만들기 위한 실행 식별값입니다.
\set seed_namespace performance-v1
\endif

\if :{?seed_batch}
\else
-- 전화번호 충돌 방지용 0~9 숫자입니다. namespace를 바꿀 때 함께 변경합니다.
\set seed_batch 1
\endif

-- 다른 DB에 데이터를 쓰기 전에 입력값을 검증합니다.
SELECT :investor_count::integer BETWEEN 1 AND 9999999 AS valid_investor_count,
       :issuer_count::integer BETWEEN 1 AND 9999999 AS valid_issuer_count,
       :offering_count::integer > 0 AS valid_offering_count,
       :offering_total_quantity::bigint > 0 AS valid_offering_total_quantity,
       :price_per_unit::bigint > 0 AS valid_price_per_unit,
       :max_subscription_quantity::bigint BETWEEN 1 AND :offering_total_quantity::bigint
           AS valid_max_subscription_quantity,
       :wallet_balance::bigint >=
           :price_per_unit::bigint * :max_subscription_quantity::bigint
           AS valid_wallet_balance,
       char_length(:'seed_namespace') BETWEEN 1 AND 50
           AND :'seed_namespace' ~ '^[a-z0-9-]+$'
           AS valid_seed_namespace,
       :seed_batch::integer BETWEEN 0 AND 9 AS valid_seed_batch
\gset

\if :valid_investor_count
\else
\echo 'investor_count must be between 1 and 9999999.'
\quit 3
\endif

\if :valid_issuer_count
\else
\echo 'issuer_count must be between 1 and 9999999.'
\quit 3
\endif

\if :valid_offering_count
\else
\echo 'offering_count must be greater than 0.'
\quit 3
\endif

\if :valid_offering_total_quantity
\else
\echo 'offering_total_quantity must be greater than 0.'
\quit 3
\endif

\if :valid_price_per_unit
\else
\echo 'price_per_unit must be greater than 0.'
\quit 3
\endif

\if :valid_max_subscription_quantity
\else
\echo 'max_subscription_quantity must be between 1 and offering_total_quantity.'
\quit 3
\endif

\if :valid_wallet_balance
\else
\echo 'wallet_balance must cover price_per_unit * max_subscription_quantity.'
\quit 3
\endif

\if :valid_seed_namespace
\else
\echo 'seed_namespace must contain 1-50 lowercase letters, numbers, or hyphens.'
\quit 3
\endif

\if :valid_seed_batch
\else
\echo 'seed_batch must be between 0 and 9.'
\quit 3
\endif
-- -----------------------------------------------------------------------------
-- 1. User DB
-- 투자자, 발행자, 관리자와 KYC/발행자 승인 이력을 생성합니다.
-- -----------------------------------------------------------------------------
\echo 'Seeding user_db...'
-- user db 접속
\connect user_db

-- 테스트 계정도 실제 로그인 API를 사용할 수 있도록 BCrypt 해시를 생성합니다.
-- 컨테이너의 PostgreSQL 초기 사용자는 확장 설치 권한을 가지고 있습니다.
-- 비밀번호를 bcrypt 방식으로 암호화 하기 위해 pgcrypto 확장 기능을 활성화 합니다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- 청약 부하 테스트에 사용할 투자자를 생성합니다.
-- 모든 투자자는 ACTIVE + VERIFIED 상태이며 KYC 유효기간은 1년입니다.
-- BCrypt 계산을 사용자마다 반복하지 않도록 CTE에서 공통 비밀번호를 한 번 생성합니다.
WITH encoded_password AS (
    SELECT crypt('Password1!', gen_salt('bf', 10)) AS value
)
INSERT INTO p_users (
    user_id, email, password, name, phone,
    role, account_status, kyc_status, kyc_expires_at,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-investor-' || n)::uuid,
    :'seed_namespace' || '-seed-investor-' || n || '@moneytown.test',
    encoded_password.value,
    '시드 투자자 ' || n,
    '010' || :seed_batch::text || lpad(n::text, 7, '0'),
    'INVESTOR', 'ACTIVE', 'VERIFIED', now() + interval '365 days',
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM generate_series(1, :investor_count::integer) AS n
CROSS JOIN encoded_password
ON CONFLICT (user_id) DO NOTHING;

-- 자산과 공모를 소유할 발행자 계정을 생성합니다.
WITH encoded_password AS (
    SELECT crypt('Password1!', gen_salt('bf', 10)) AS value
)
INSERT INTO p_users (
    user_id, email, password, name, phone,
    role, account_status, kyc_status, kyc_expires_at,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-issuer-' || n)::uuid,
    :'seed_namespace' || '-seed-issuer-' || n || '@moneytown.test',
    encoded_password.value,
    '시드 발행자 ' || n,
    '019' || :seed_batch::text || lpad(n::text, 7, '0'),
    'ISSUER', 'ACTIVE', 'VERIFIED', now() + interval '365 days',
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM generate_series(1, :issuer_count::integer) AS n
CROSS JOIN encoded_password
ON CONFLICT (user_id) DO NOTHING;

-- KYC, 발행자, 자산, 공모 심사에 사용할 관리자 한 명을 생성합니다.
WITH encoded_password AS (
    SELECT crypt('Password1!', gen_salt('bf', 10)) AS value
)
INSERT INTO p_users (
    user_id, email, password, name, phone,
    role, account_status, kyc_status,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-admin')::uuid,
    :'seed_namespace' || '-seed-admin@moneytown.test',
    encoded_password.value,
    '시드 관리자',
    '018' || :seed_batch::text || '0000000',
    'ADMIN', 'ACTIVE', 'NOT_SUBMITTED',
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM encoded_password
ON CONFLICT (user_id) DO NOTHING;

-- p_users의 현재 KYC 상태만 맞추는 데서 끝내지 않고 승인 이력도 함께 생성합니다.
INSERT INTO p_kyc_verifications (
    kyc_verification_id, user_id, occupation_type, fund_source,
    status, consent_version, domestic_resident, attempt_no,
    consented_at, submitted_at, reviewed_at, reviewed_by,
    verified_at, expires_at,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-kyc-investor-' || n)::uuid,
    md5(:'seed_namespace' || '-seed-investor-' || n)::uuid,
    'OFFICE_WORKER', 'SALARY', 'VERIFIED', 'v1.0', true, 1,
    now(), now(), now(), md5(:'seed_namespace' || '-seed-admin')::uuid,
    now(), now() + interval '365 days',
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM generate_series(1, :investor_count::integer) AS n
ON CONFLICT (kyc_verification_id) DO NOTHING;

-- 발행자도 공모 관리 자격 검증을 통과해야 하므로 승인된 KYC 이력을 생성합니다.
INSERT INTO p_kyc_verifications (
    kyc_verification_id, user_id, occupation_type, fund_source,
    status, consent_version, domestic_resident, attempt_no,
    consented_at, submitted_at, reviewed_at, reviewed_by,
    verified_at, expires_at,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-kyc-issuer-' || n)::uuid,
    md5(:'seed_namespace' || '-seed-issuer-' || n)::uuid,
    'BUSINESS_OWNER', 'BUSINESS_INCOME', 'VERIFIED', 'v1.0', true, 1,
    now(), now(), now(), md5(:'seed_namespace' || '-seed-admin')::uuid,
    now(), now() + interval '365 days',
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM generate_series(1, :issuer_count::integer) AS n
ON CONFLICT (kyc_verification_id) DO NOTHING;

-- 발행자 권한이 승인되었다는 업무 이력을 생성합니다.
INSERT INTO p_issuer_applications (
    issuer_application_id, user_id, application_reason, status,
    applied_at, reviewed_at, reviewed_by,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-issuer-application-' || n)::uuid,
    md5(:'seed_namespace' || '-seed-issuer-' || n)::uuid,
    '성능 테스트용 발행자 신청', 'APPROVED',
    now(), now(), md5(:'seed_namespace' || '-seed-admin')::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM generate_series(1, :issuer_count::integer) AS n
ON CONFLICT (issuer_application_id) DO NOTHING;

COMMIT;
ANALYZE p_users;
ANALYZE p_kyc_verifications;

-- -----------------------------------------------------------------------------
-- 2. Wallet DB
-- 투자자별 지갑과 최초 충전 원장을 생성합니다.
-- User DB와 물리적인 외래 키가 없으므로 동일한 결정적 user_id를 사용해 연결합니다.
-- -----------------------------------------------------------------------------
\echo 'Seeding wallet_db...'
\connect wallet_db

BEGIN;

-- 초기에는 동결 금액이 없으므로 balance와 available_balance가 같아야 합니다.
-- ck_wallets_available_balance: available_balance = balance - hold_balance
INSERT INTO p_wallets (
    user_id, balance, hold_balance, available_balance,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-investor-' || n)::uuid,
    :wallet_balance::bigint, 0, :wallet_balance::bigint,
    now(), '00000000-0000-0000-0000-000000000000'::uuid,
    now(), '00000000-0000-0000-0000-000000000000'::uuid, false
FROM generate_series(1, :investor_count::integer) AS n
ON CONFLICT (user_id) DO NOTHING;

-- 지갑 잔액만 입력하면 금융 원장과 현재 잔액이 불일치하므로 DEPOSIT 원장도 생성합니다.
-- idempotency_key는 테이블 전체에서 UNIQUE이므로 투자자 순번을 포함합니다.
INSERT INTO p_wallet_transactions (
    wallet_id, type, amount, balance_before, balance_after,
    idempotency_key, reference_id, created_at, created_by
)
SELECT
    w.wallet_id, 'DEPOSIT', :wallet_balance::bigint, 0, :wallet_balance::bigint,
    :'seed_namespace' || '-seed-deposit-' || n, NULL, now(),
    '00000000-0000-0000-0000-000000000000'::uuid
FROM generate_series(1, :investor_count::integer) AS n
JOIN p_wallets w
  ON w.user_id = md5(:'seed_namespace' || '-seed-investor-' || n)::uuid
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
ANALYZE p_wallets;
ANALYZE p_wallet_transactions;

-- -----------------------------------------------------------------------------
-- 3. Asset DB
-- 공모 등록에 사용할 승인 완료 자산을 생성합니다.
-- 자산 수는 offering_count와 같고, 발행자에게 순환 방식으로 배정합니다.
-- -----------------------------------------------------------------------------
\echo 'Seeding asset_db...'
\connect asset_db

BEGIN;

-- 수량과 단가는 offering_total_quantity, price_per_unit 옵션으로 지정합니다.
-- valuation_amount = unit_price * total_share_quantity이므로 rounding_difference_amount는 0입니다.
INSERT INTO p_assets (
    asset_id, user_id, asset_type, asset_name, owner_name, description,
    valuation_amount, expected_return_rate, detail_data,
    unit_price, total_share_quantity, rounding_difference_amount,
    allocated_quantity, asset_status, version,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-asset-' || n)::uuid,
    md5(:'seed_namespace' || '-seed-issuer-'
        || (((n - 1) % :issuer_count::integer) + 1))::uuid,
    CASE WHEN n % 2 = 0 THEN 'REAL_ESTATE' ELSE 'MUSIC_COPYRIGHT' END,
    '시드 자산 ' || n,
    '시드 발행자 ' || (((n - 1) % :issuer_count::integer) + 1),
    '청약 성능 테스트용 승인 자산',
    :offering_total_quantity::bigint * :price_per_unit::bigint,
    5.0000,
    jsonb_build_object('seed', true, 'sequence', n),
    :price_per_unit::bigint,
    :offering_total_quantity::bigint,
    0, 0, 'APPROVED', 0,
    now(), md5(:'seed_namespace' || '-seed-issuer-'
               || (((n - 1) % :issuer_count::integer) + 1))::uuid,
    now(), md5(:'seed_namespace' || '-seed-issuer-'
               || (((n - 1) % :issuer_count::integer) + 1))::uuid,
    false
FROM generate_series(1, :offering_count::integer) AS n
ON CONFLICT (asset_id) DO NOTHING;

COMMIT;
ANALYZE p_assets;

-- -----------------------------------------------------------------------------
-- 4. Offering DB
-- 청약 API가 즉시 사용할 수 있도록 현재 모집 기간에 포함된 OPEN 공모를 생성합니다.
-- 자산과 공모는 1:1이며 Asset DB와 동일한 결정적 asset_id를 사용합니다.
-- -----------------------------------------------------------------------------
\echo 'Seeding offering_db...'
\connect offering_db

BEGIN;

-- 시작 시각은 1시간 전, 종료 시각은 7일 후입니다.
-- 공모별 모집 수량은 자산의 전체 지분 수량과 동일하고 초기 잔여 수량도 동일합니다.
INSERT INTO p_offerings (
    offering_id, asset_id, issuer_id, title,
    price_per_unit, total_quantity, remaining_quantity,
    min_subscription_quantity, max_subscription_quantity,
    start_at, end_at, offering_status,
    review_requested_at, reviewed_at, reviewed_by,
    created_at, created_by, updated_at, updated_by, is_deleted
)
SELECT
    md5(:'seed_namespace' || '-seed-offering-' || n)::uuid,
    md5(:'seed_namespace' || '-seed-asset-' || n)::uuid,
    md5(:'seed_namespace' || '-seed-issuer-'
        || (((n - 1) % :issuer_count::integer) + 1))::uuid,
    '시드 자산 ' || n || ' 공모',
    :price_per_unit::bigint,
    :offering_total_quantity::bigint,
    :offering_total_quantity::bigint,
    1, :max_subscription_quantity::bigint,
    now() - interval '1 hour', now() + interval '7 days', 'OPEN',
    now() - interval '2 days', now() - interval '1 day',
    md5(:'seed_namespace' || '-seed-admin')::uuid,
    now() - interval '2 days',
    md5(:'seed_namespace' || '-seed-issuer-'
        || (((n - 1) % :issuer_count::integer) + 1))::uuid,
    now(),
    md5(:'seed_namespace' || '-seed-issuer-'
        || (((n - 1) % :issuer_count::integer) + 1))::uuid,
    false
FROM generate_series(1, :offering_count::integer) AS n
ON CONFLICT (offering_id) DO NOTHING;

COMMIT;

-- 대량 입력 후 PostgreSQL 옵티마이저가 최신 통계를 사용하도록 갱신합니다.
ANALYZE p_offerings;

\echo 'Seed completed.'
\echo 'All seeded accounts use password: Password1!'

-- JMeter 준비에 필요한 대표 공모와 계정 범위를 출력합니다.
SELECT :'seed_namespace' AS seed_namespace,
       :investor_count::integer AS investor_count,
       md5(:'seed_namespace' || '-seed-offering-1')::uuid
           AS primary_offering_id,
       :'seed_namespace' || '-seed-investor-1@moneytown.test'
           AS first_investor_email,
       :'seed_namespace' || '-seed-investor-'
           || :investor_count::integer || '@moneytown.test'
           AS last_investor_email,
       :offering_total_quantity::bigint AS offering_total_quantity;

