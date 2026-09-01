-- Analysis Service — FDS 테이블 생성 (FdsUserState / FdsDetectionLog).
-- spring.jpa.hibernate.ddl-auto=validate 이므로 컬럼명·타입은 엔티티와 정확히 일치해야 한다.
-- Instant 필드는 Hibernate 가 TIMESTAMP_UTC 로 매핑하므로 TIMESTAMPTZ 를 사용한다.

-- 사용자별 FDS 상태 (BaseUpdatableEntity: 감사 + 논리 삭제 컬럼 포함)
CREATE TABLE p_fds_user_states
(
    fds_user_state_id UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    blocked_at        TIMESTAMPTZ,
    block_reason      VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID         NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        UUID         NOT NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    CONSTRAINT pk_fds_user_states PRIMARY KEY (fds_user_state_id),
    CONSTRAINT uk_fds_user_states_user_id UNIQUE (user_id)
);

-- FDS 탐지 로그 (BaseEntity 만 상속 = Append-Only, 수정/논리삭제 컬럼 없음)
CREATE TABLE p_fds_detection_logs
(
    fds_detection_log_id UUID        NOT NULL,
    request_id           UUID,
    event_id             UUID,
    user_id              UUID        NOT NULL,
    asset_id             UUID,
    detection_type       VARCHAR(10) NOT NULL,
    event_type           VARCHAR(50) NOT NULL,
    rule_code            VARCHAR(50) NOT NULL,
    observed_value       INTEGER     NOT NULL,
    threshold_value      INTEGER     NOT NULL,
    occurred_at          TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    created_by           UUID        NOT NULL,
    CONSTRAINT pk_fds_detection_logs PRIMARY KEY (fds_detection_log_id)
);

-- 탐지 로그 조회 API: user_id 필터 + occurred_at DESC 정렬/페이징 대응
-- 엔티티의 @Index(name = "idx_fds_detection_user") 와 이름을 맞춘다.
CREATE INDEX idx_fds_detection_user
    ON p_fds_detection_logs (user_id, occurred_at DESC);
