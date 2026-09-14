ALTER TABLE p_subscriptions
    ADD COLUMN wallet_hold_failure_code VARCHAR(50),
    ADD COLUMN subscription_failure_code VARCHAR(50);

-- Wallet에서 확정한 실패 코드와 과거 임시 코드를 Wallet 실패로 이관
UPDATE p_subscriptions
SET wallet_hold_failure_code = failure_code
WHERE failure_code IN (
                       'WALLET_NOT_FOUND',
                       'INSUFFICIENT_AVAILABLE_BALANCE',
                       'INSUFFICIENT_BALANCE',
                       'INVALID_AMOUNT',
                       'BALANCE_OVERFLOW'
    );

-- 나머지는 Offering 내부 실패 코드로 이관
UPDATE p_subscriptions
SET subscription_failure_code = failure_code
WHERE failure_code IS NOT NULL
  AND failure_code NOT IN (
                           'WALLET_NOT_FOUND',
                           'INSUFFICIENT_AVAILABLE_BALANCE',
                           'INSUFFICIENT_BALANCE',
                           'INVALID_AMOUNT',
                           'BALANCE_OVERFLOW'
    );

ALTER TABLE p_subscriptions
    DROP COLUMN failure_code;