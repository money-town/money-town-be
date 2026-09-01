CREATE TABLE p_wallets (
    wallet_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    hold_balance BIGINT NOT NULL DEFAULT 0 CHECK (hold_balance >= 0),
    available_balance BIGINT NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id),
    CONSTRAINT ck_wallets_hold_balance CHECK (hold_balance <= balance),
    CONSTRAINT ck_wallets_available_balance CHECK (available_balance = balance - hold_balance),
    CONSTRAINT ck_wallets_deletion
        CHECK ((is_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
            OR (is_deleted = TRUE AND deleted_at IS NOT NULL))
);

CREATE TABLE p_wallet_transactions (
    transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES p_wallets(wallet_id),
    type VARCHAR(20) NOT NULL
        CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'HOLD', 'UNHOLD', 'DEDUCT', 'DIVIDEND', 'REFUND', 'SETTLEMENT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    balance_before BIGINT NOT NULL CHECK (balance_before >= 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    idempotency_key VARCHAR(255) NOT NULL,
    reference_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT uk_wallet_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE p_wallet_holds (
    hold_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES p_wallets(wallet_id),
    subscription_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'HELD'
        CHECK (status IN ('HELD', 'RELEASED', 'COMMITTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    CONSTRAINT uk_wallet_holds_subscription_id UNIQUE (subscription_id)
);

CREATE INDEX idx_wallet_transactions_wallet_id_created_at ON p_wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX idx_wallet_holds_wallet_id ON p_wallet_holds(wallet_id);
