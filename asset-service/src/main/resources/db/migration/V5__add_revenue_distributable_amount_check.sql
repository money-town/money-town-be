-- 배당 가능 금액이 음수가 되지 않도록 총수익 금액을 검증한다.
ALTER TABLE p_revenues
    ADD CONSTRAINT ck_revenues_distributable_amount
        CHECK (gross_amount >= expense_amount + fee_amount);