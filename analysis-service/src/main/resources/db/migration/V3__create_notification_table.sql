CREATE TABLE p_slack_notifications (
    notification_id   UUID         NOT NULL,
    idempotency_key    UUID         NOT NULL,
    user_id           UUID,
    notification_type VARCHAR(50)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    message           TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    sent_at           TIMESTAMPTZ,
    error_message     TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID         NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        UUID         NOT NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    CONSTRAINT pk_slack_notifications PRIMARY KEY (notification_id),
    CONSTRAINT uk_slack_notifications_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_slack_notifications_created_at ON p_slack_notifications (created_at DESC);
CREATE INDEX idx_slack_notifications_user_id ON p_slack_notifications (user_id);