-- 관리자 전체 사용자 목록 조회
CREATE INDEX IF NOT EXISTS idx_p_users_active_created_at
    ON p_users (created_at DESC)
    WHERE is_deleted = FALSE;

-- 상태별 KYC 심사 목록 조회
CREATE INDEX IF NOT EXISTS idx_p_kyc_status_submitted_at
    ON p_kyc_verifications (status, submitted_at DESC)
    WHERE is_deleted = FALSE;

-- 상태 조건 없이 KYC 목록을 조회하는 경우
CREATE INDEX IF NOT EXISTS idx_p_kyc_submitted_at
    ON p_kyc_verifications (submitted_at DESC)
    WHERE is_deleted = FALSE;