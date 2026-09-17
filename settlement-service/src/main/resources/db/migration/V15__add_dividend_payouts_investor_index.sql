CREATE INDEX IF NOT EXISTS idx_dividend_payouts_investor
    ON p_dividend_payouts (investor_id, updated_at DESC, dividend_payout_id ASC)
    WHERE is_deleted = false;