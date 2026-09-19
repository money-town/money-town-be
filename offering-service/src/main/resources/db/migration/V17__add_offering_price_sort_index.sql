-- 공개 공모 목록의 가격 내림차순 정렬과 깊은 offset 조회를 지원한다.
-- offering_id는 동일 가격에서 결정적인 페이지 순서를 보장하는 tie-breaker다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_offerings_active_status_price
    ON p_offerings (
        offering_status,
        price_per_unit DESC,
        offering_id DESC
    )
    WHERE is_deleted = FALSE;
