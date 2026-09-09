CREATE TABLE p_issuer_applications
(
    issuer_application_id UUID         NOT NULL,
    user_id                UUID         NOT NULL,
    application_reason     VARCHAR(500) NOT NULL,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    applied_at             TIMESTAMPTZ  NOT NULL,
    reviewed_at            TIMESTAMPTZ  NULL,
    reviewed_by            UUID         NULL,
    rejection_reason       VARCHAR(500) NULL,

    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             UUID         NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             UUID         NOT NULL,
    is_deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at             TIMESTAMPTZ  NULL,
    deleted_by             UUID         NULL,

    CONSTRAINT pk_p_issuer_applications
        PRIMARY KEY (issuer_application_id),

    CONSTRAINT fk_p_issuer_applications_user
        FOREIGN KEY (user_id)
            REFERENCES p_users (user_id),

    CONSTRAINT ck_p_issuer_applications_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_p_issuer_applications_user_status
    ON p_issuer_applications (user_id, status);

CREATE UNIQUE INDEX uk_p_issuer_applications_pending_user
    ON p_issuer_applications (user_id)
    WHERE status = 'PENDING'
      AND is_deleted = FALSE;

COMMENT ON TABLE p_issuer_applications
    IS '발행자 권한 신청 및 관리자 심사 이력';

COMMENT ON COLUMN p_issuer_applications.application_reason
    IS '발행자 권한 신청 사유';

COMMENT ON COLUMN p_issuer_applications.reviewed_by
    IS '신청을 심사한 관리자 사용자 식별자';
