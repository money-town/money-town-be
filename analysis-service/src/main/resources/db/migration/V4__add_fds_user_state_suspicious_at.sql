-- Analysis Service - FdsUserState에 SUSPICOUS 전이 시각(suspicious_at) 추가
ALTER TABLE p_fds_user_states
    ADD COLUMN suspicious_at TIMESTAMPTZ;