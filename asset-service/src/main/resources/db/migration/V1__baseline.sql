-- Asset Service 초기 스키마. 단가 절사 차액만 저장하며 이자/소유주 납부 기능은 포함하지 않는다.

CREATE TABLE p_assets (
    asset_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    asset_type VARCHAR(30) NOT NULL CHECK (asset_type IN ('REAL_ESTATE', 'MUSIC_COPYRIGHT')),
    asset_name VARCHAR(200) NOT NULL CHECK (char_length(asset_name) BETWEEN 2 AND 200),
    owner_name VARCHAR(200),
    description TEXT NOT NULL,
    valuation_amount BIGINT NOT NULL CHECK (valuation_amount > 0),
    expected_return_rate NUMERIC(7, 4) NOT NULL DEFAULT 0 CHECK (expected_return_rate >= 0),
    detail_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    total_share_quantity BIGINT NOT NULL CHECK (total_share_quantity > 0),
    rounding_difference_amount BIGINT NOT NULL CHECK (rounding_difference_amount >= 0),
    allocated_quantity BIGINT NOT NULL DEFAULT 0,
    asset_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (asset_status IN ('DRAFT', 'REVIEW_REQUESTED', 'APPROVED', 'REJECTED', 'SUSPENDED', 'TERMINATED')),
    rejection_reason VARCHAR(500),
    representative_image_key VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT ck_assets_rounding_difference
        CHECK (unit_price = valuation_amount / total_share_quantity
            AND rounding_difference_amount = valuation_amount::numeric - unit_price::numeric * total_share_quantity),
    CONSTRAINT ck_assets_allocated_quantity
        CHECK (allocated_quantity >= 0 AND allocated_quantity <= total_share_quantity),
    CONSTRAINT ck_assets_rejection_reason
        CHECK (asset_status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT ck_assets_deletion
        CHECK ((is_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
            OR (is_deleted = TRUE AND deleted_at IS NOT NULL))
);

CREATE TABLE p_asset_documents (
    document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES p_assets(asset_id),
    document_type VARCHAR(30) NOT NULL
        CHECK (document_type IN ('INVESTMENT_GUIDE', 'APPRAISAL', 'RIGHT_PROOF', 'ETC')),
    document_version INTEGER NOT NULL DEFAULT 1 CHECK (document_version > 0),
    original_filename VARCHAR(255) NOT NULL,
    s3_object_key VARCHAR(500) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    file_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    CONSTRAINT uk_asset_documents_version UNIQUE (asset_id, document_type, document_version),
    CONSTRAINT ck_asset_documents_deletion
        CHECK ((is_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
            OR (is_deleted = TRUE AND deleted_at IS NOT NULL))
);

CREATE TABLE p_holdings (
    holding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES p_assets(asset_id),
    user_id UUID NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT uk_holdings_asset_user UNIQUE (asset_id, user_id)
);

CREATE TABLE p_holding_histories (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id UUID NOT NULL REFERENCES p_holdings(holding_id),
    subscription_id UUID,
    history_type VARCHAR(30) NOT NULL
        CHECK (history_type IN ('ALLOCATE', 'REVOKE', 'TRANSFER', 'ADJUSTMENT')),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    balance_before BIGINT NOT NULL CHECK (balance_before >= 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    CONSTRAINT ck_holding_histories_subscription
        CHECK (history_type NOT IN ('ALLOCATE', 'REVOKE') OR subscription_id IS NOT NULL),
    CONSTRAINT ck_holding_histories_reason
        CHECK (history_type = 'ALLOCATE' OR reason IS NOT NULL)
);

CREATE UNIQUE INDEX uk_holding_histories_subscription_type
    ON p_holding_histories(subscription_id, history_type)
    WHERE history_type IN ('ALLOCATE', 'REVOKE');

CREATE TABLE p_revenues (
    revenue_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES p_assets(asset_id),
    user_id UUID NOT NULL,
    source_type VARCHAR(30) NOT NULL
        CHECK (source_type IN ('PROPERTY_MANAGER', 'MUSIC_TRUST', 'ADMIN', 'SYNTHETIC')),
    source_reference_id VARCHAR(100) NOT NULL,
    revenue_type VARCHAR(30) NOT NULL
        CHECK (revenue_type IN ('RENTAL_INCOME', 'COPYRIGHT_ROYALTY', 'ETC')),
    gross_amount DECIMAL(19, 2) NOT NULL CHECK (gross_amount > 0),
    expense_amount DECIMAL(19, 2) NOT NULL DEFAULT 0 CHECK (expense_amount >= 0),
    fee_amount DECIMAL(19, 2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW' CHECK (currency = 'KRW'),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'READY'
        CHECK (transfer_status IN ('READY', 'TRANSFERRED', 'FAILED')),
    transferred_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT uk_revenues_source UNIQUE (asset_id, source_type, source_reference_id),
    CONSTRAINT ck_revenues_period CHECK (period_start <= period_end),
    CONSTRAINT ck_revenues_transfer
        CHECK ((transfer_status = 'READY' AND transferred_at IS NULL AND failure_reason IS NULL)
            OR (transfer_status = 'TRANSFERRED' AND transferred_at IS NOT NULL AND failure_reason IS NULL)
            OR (transfer_status = 'FAILED' AND transferred_at IS NULL AND failure_reason IS NOT NULL))
);

CREATE INDEX idx_assets_user_id ON p_assets(user_id);
CREATE INDEX idx_asset_documents_asset_id ON p_asset_documents(asset_id);
CREATE INDEX idx_holdings_user_id ON p_holdings(user_id);
CREATE INDEX idx_holding_histories_holding_id ON p_holding_histories(holding_id);
CREATE INDEX idx_revenues_asset_created_at ON p_revenues(asset_id, created_at DESC);
CREATE INDEX idx_revenues_transfer_status_created_at ON p_revenues(transfer_status, created_at);

CREATE INDEX idx_holding_histories_subscription_created_at
    ON p_holding_histories (subscription_id, created_at);
