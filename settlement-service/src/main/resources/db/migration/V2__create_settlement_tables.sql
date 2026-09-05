CREATE TABLE p_settlement_batches (
    settlement_batch_id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    revenue_id UUID NOT NULL,
    record_date TIMESTAMPTZ NOT NULL,
    total_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    carried_in_amount BIGINT NOT NULL,
    remainder_amount BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT uk_settlement_batches_revenue_id UNIQUE (revenue_id)
);

CREATE TABLE p_holdings_snapshots (
    holding_snapshot_id UUID PRIMARY KEY,
    settlement_batch_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL,
    total_quantity BIGINT NOT NULL,
    total_holders INTEGER NOT NULL,
    total_share_quantity BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    CONSTRAINT uk_holdings_snapshots_settlement_batch_id UNIQUE (settlement_batch_id)
);

CREATE TABLE p_dividend_payouts (
    dividend_payout_id UUID PRIMARY KEY,
    settlement_batch_id UUID NOT NULL,
    investor_id UUID NOT NULL,
    share_ratio NUMERIC(10,8) NOT NULL,
    amount BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT uk_dividend_payouts_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE p_final_settlement_batches (
    final_settlement_batch_id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    terminated_at TIMESTAMPTZ NOT NULL,
    unit_price BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT uk_final_settlement_batches_asset_id UNIQUE (asset_id)
);

CREATE TABLE p_final_settlement_payouts (
    final_settlement_payout_id UUID PRIMARY KEY,
    final_settlement_batch_id UUID NOT NULL,
    investor_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT uk_final_settlement_payouts_idempotency_key UNIQUE (idempotency_key)
);