CREATE TABLE p_kyc_verifications
(
    kyc_verification_id UUID         NOT NULL,
    user_id              UUID         NOT NULL,
    occupation_type      VARCHAR(30)  NOT NULL,
    fund_source           VARCHAR(30)  NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    consent_version       VARCHAR(20)  NOT NULL,
    domestic_resident     BOOLEAN      NOT NULL,
    attempt_no            INTEGER      NOT NULL DEFAULT 1,
    consented_at          TIMESTAMPTZ  NOT NULL,
    submitted_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at           TIMESTAMPTZ  NULL,
    reviewed_by           UUID         NULL,
    verified_at           TIMESTAMPTZ  NULL,
    expires_at            TIMESTAMPTZ  NULL,
    rejection_reason      VARCHAR(500) NULL,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            UUID         NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            UUID         NOT NULL,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ  NULL,
    deleted_by            UUID         NULL,

    CONSTRAINT pk_p_kyc_verifications
        PRIMARY KEY (kyc_verification_id),

    CONSTRAINT fk_p_kyc_verifications_user
        FOREIGN KEY (user_id)
            REFERENCES p_users (user_id),

    CONSTRAINT uk_p_kyc_verifications_user_attempt
        UNIQUE (user_id, attempt_no),

    CONSTRAINT ck_p_kyc_verifications_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')),

    CONSTRAINT ck_p_kyc_verifications_attempt_no
        CHECK (attempt_no > 0)
);

CREATE INDEX idx_p_kyc_verifications_user_id
    ON p_kyc_verifications (user_id);

CREATE INDEX idx_p_kyc_verifications_status
    ON p_kyc_verifications (status);

CREATE INDEX idx_p_kyc_verifications_expires_at
    ON p_kyc_verifications (expires_at);

COMMENT ON TABLE p_kyc_verifications
    IS '사용자의 KYC 신청 내용과 심사 이력 관리';

COMMENT ON COLUMN p_kyc_verifications.kyc_verification_id
    IS 'KYC 신청 및 심사 이력 고유 식별자';

COMMENT ON COLUMN p_kyc_verifications.user_id
    IS 'KYC를 신청한 사용자 식별자';

COMMENT ON COLUMN p_kyc_verifications.occupation_type
    IS '사용자가 제출한 직업 유형';

COMMENT ON COLUMN p_kyc_verifications.fund_source
    IS '사용자가 제출한 투자 자금 출처';

COMMENT ON COLUMN p_kyc_verifications.status
    IS 'KYC 심사 상태: PENDING, VERIFIED, REJECTED, EXPIRED';

COMMENT ON COLUMN p_kyc_verifications.consent_version
    IS '사용자가 동의한 KYC 약관 버전';

COMMENT ON COLUMN p_kyc_verifications.domestic_resident
    IS '국내 거주 여부';

COMMENT ON COLUMN p_kyc_verifications.attempt_no
    IS '사용자별 KYC 신청 회차';

COMMENT ON COLUMN p_kyc_verifications.consented_at
    IS 'KYC 약관 동의 시각';

COMMENT ON COLUMN p_kyc_verifications.submitted_at
    IS 'KYC 신청서 제출 시각';

COMMENT ON COLUMN p_kyc_verifications.reviewed_at
    IS '관리자 심사 완료 시각';

COMMENT ON COLUMN p_kyc_verifications.reviewed_by
    IS 'KYC를 심사한 관리자 사용자 식별자';

COMMENT ON COLUMN p_kyc_verifications.verified_at
    IS 'KYC 인증 승인 시각';

COMMENT ON COLUMN p_kyc_verifications.expires_at
    IS 'KYC 인증 만료 시각';

COMMENT ON COLUMN p_kyc_verifications.rejection_reason
    IS 'KYC 심사 거절 사유';
