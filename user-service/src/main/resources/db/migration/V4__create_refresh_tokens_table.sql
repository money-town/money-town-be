CREATE TABLE p_refresh_tokens
(
    refresh_token_id UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    token_id         VARCHAR(36) NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       UUID        NOT NULL,

    CONSTRAINT pk_p_refresh_tokens
        PRIMARY KEY (refresh_token_id),

    CONSTRAINT fk_p_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES p_users (user_id),

    CONSTRAINT uk_p_refresh_tokens_token_id
        UNIQUE (token_id)
);

CREATE INDEX idx_p_refresh_tokens_user_active
    ON p_refresh_tokens (user_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_p_refresh_tokens_expires_at
    ON p_refresh_tokens (expires_at);

COMMENT ON TABLE p_refresh_tokens
    IS 'Redis를 사용하지 않는 환경의 Refresh Token 식별자 및 폐기 상태 관리';

COMMENT ON COLUMN p_refresh_tokens.token_id
    IS 'Refresh Token JWT의 jti 값';

COMMENT ON COLUMN p_refresh_tokens.revoked_at
    IS '로그아웃 또는 재발급으로 토큰이 폐기된 시각';
