-- 관리자 전체 공모 목록(GET /api/v1/offerings/manage)은 offeringStatus 필터 없이
-- createdAt DESC로 정렬되는 경우가 있다. 기존 idx_offerings_active_status_created는
-- offering_status가 선두 컬럼이라 status 필터가 없는 조회의 전역 정렬을 커버하지 못한다.
-- status 필터 유무와 무관하게 최신순 정렬을 지원하는 인덱스를 추가한다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_offerings_active_created
    ON p_offerings (
    created_at DESC,
    offering_id DESC
    )
    WHERE is_deleted = FALSE;
