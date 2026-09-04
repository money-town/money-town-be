-- 사용자 계정 및 현재 상태 테이블 생성
CREATE TABLE p_users
(
    user_id       UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,

    role           VARCHAR(30) NOT NULL DEFAULT 'INVESTOR',
    account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    kyc_status     VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    kyc_expires_at TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID        NOT NULL,

    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ NULL,
    deleted_by UUID        NULL,

    CONSTRAINT pk_p_users
        PRIMARY KEY (user_id),

    CONSTRAINT uk_p_users_email
        UNIQUE (email),

    CONSTRAINT uk_p_users_phone
        UNIQUE (phone),

    CONSTRAINT ck_p_users_role
        CHECK (
            role IN (
                     'INVESTOR',
                     'ISSUER',
                     'ADMIN'
                )
            ),

    CONSTRAINT ck_p_users_account_status
        CHECK (
            account_status IN (
                               'ACTIVE',
                               'SUSPENDED',
                               'WITHDRAWN'
                )
            ),

    CONSTRAINT ck_p_users_kyc_status
        CHECK (
            kyc_status IN (
                           'NOT_SUBMITTED',
                           'PENDING',
                           'VERIFIED',
                           'REJECTED',
                           'EXPIRED'
                )
            )
);

CREATE INDEX idx_p_users_account_status
    ON p_users (account_status);

CREATE INDEX idx_p_users_kyc_status
    ON p_users (kyc_status);

CREATE INDEX idx_p_users_is_deleted
    ON p_users (is_deleted);

COMMENT ON TABLE p_users
    IS '사용자 계정 및 현재 KYC 상태 관리';

COMMENT ON COLUMN p_users.user_id
    IS '사용자 고유 식별자';

COMMENT ON COLUMN p_users.email
    IS '로그인에 사용하는 이메일';

COMMENT ON COLUMN p_users.password
    IS '단방향 암호화된 비밀번호';

COMMENT ON COLUMN p_users.name
    IS '사용자 이름';

COMMENT ON COLUMN p_users.phone
    IS '사용자 휴대전화 번호';

COMMENT ON COLUMN p_users.role
    IS '사용자 권한: INVESTOR, ISSUER, ADMIN';

COMMENT ON COLUMN p_users.account_status
    IS '계정 상태: ACTIVE, SUSPENDED, WITHDRAWN';

COMMENT ON COLUMN p_users.kyc_status
    IS '현재 KYC 상태';

COMMENT ON COLUMN p_users.kyc_expires_at
    IS 'KYC 인증 만료 시각';

COMMENT ON COLUMN p_users.is_deleted
    IS '논리 삭제 여부';
