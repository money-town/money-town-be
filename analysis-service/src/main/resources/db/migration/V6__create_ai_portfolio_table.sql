-- Analysis Service — AI 포트폴리오 추천 테이블 생성 (Portfolio).
-- spring.jpa.hibernate.ddl-auto=validate 이므로 컬럼명·타입은 엔티티와 정확히 일치해야 한다.
-- Instant 필드는 Hibernate 가 TIMESTAMP_UTC 로 매핑하므로 TIMESTAMPTZ 를 사용한다.
-- response 는 LLM 응답 JSON 을 문자열 그대로 저장 (검증은 저장 전 서비스 레이어에서 수행). 필요 시 이후 jsonb 로 승격.

CREATE TABLE p_ai_portfolios
(
    ai_portfolio_id     UUID         NOT NULL,
    idempotency_key     UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    investment_amount   BIGINT       NOT NULL,
    risk_type           VARCHAR(20)  NOT NULL,
    prefered_asset_type VARCHAR(30),
    status              VARCHAR(20)  NOT NULL,
    response            TEXT,
    error_message       TEXT,
    model               VARCHAR(100),
    prompt_version      VARCHAR(30),
    processing_time_ms  BIGINT,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID         NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          UUID         NOT NULL,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID,
    CONSTRAINT pk_ai_portfolios PRIMARY KEY (ai_portfolio_id),
    CONSTRAINT uk_ai_portfolios_idempotency_key UNIQUE (idempotency_key)
);

-- 내 포트폴리오 목록: user_id 필터 + created_at DESC 정렬/페이징
CREATE INDEX idx_ai_portfolios_user_created
    ON p_ai_portfolios (user_id, created_at DESC);

-- 관리자 전체 목록: created_at DESC 정렬/페이징
CREATE INDEX idx_ai_portfolios_created
    ON p_ai_portfolios (created_at DESC);
