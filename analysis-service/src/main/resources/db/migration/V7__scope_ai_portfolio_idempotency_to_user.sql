-- 멱등키를 사용자 범위로 스코프한다.
-- 전역 유니크(uk_ai_portfolios_idempotency_key)는 다른 사용자가 같은 UUID를 제출하면
-- 첫 사용자의 포트폴리오를 반환하는 문제가 있어 (user_id, idempotency_key) 복합 유니크로 교체한다.

ALTER TABLE p_ai_portfolios
    DROP CONSTRAINT uk_ai_portfolios_idempotency_key;

ALTER TABLE p_ai_portfolios
    ADD CONSTRAINT uk_ai_portfolios_user_idempotency
        UNIQUE (user_id, idempotency_key);