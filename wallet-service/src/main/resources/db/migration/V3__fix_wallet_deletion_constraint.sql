-- p_wallets는 V2 직후 항상 비어있고, 소프트삭제(is_deleted=true)를 실행하는 코드가
-- 아직 없어서(UserWithdrawn 컨슈머 미구현) 이 제약을 위반하는 기존 행이 존재할 수 없다.
-- 결론 => 코드래빗 리뷰 반영 안 해도 안전함.
ALTER TABLE p_wallets DROP CONSTRAINT ck_wallets_deletion;

ALTER TABLE p_wallets ADD CONSTRAINT ck_wallets_deletion
    CHECK ((is_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
        OR (is_deleted = TRUE AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL));