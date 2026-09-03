package com.moneykk.moneytown.offering.offering.domain.repository;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OfferingRepository extends JpaRepository<Offering, UUID> {

    Optional<Offering> findByOfferingIdAndIsDeletedFalse(UUID offeringId);


    // TODO: PostgreSQL 기반 DB 통합 테스트 추가
    // - remainingQuantity == 요청 수량이면 SOLD_OUT 전환 검증
    // - remainingQuantity > 요청 수량이면 OPEN 유지 검증
    // - remainingQuantity < 요청 수량이면 UPDATE 0건 검증
    // - 동시 요청 시 remainingQuantity 음수 및 초과발행 방지 검증
    /**
     * 선착순 청약 수량을 원자적으로 확보한다.
     *
     * OPEN 상태이고 모집 기간 내이며 잔여 수량이 충분한 경우에만 차감한다.
     * 차감 결과 '잔여 수량이 0이 되면 SOLD_OUT 상태로 변경'한다.
     * OPEN → SOLD_OUT
     *
     * @return 영향받은 행 수 (1: 수량 확보 성공, 0: 확보 실패)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Offering o
           SET o.remainingQuantity = o.remainingQuantity - :quantity,
               o.offeringStatus =
                   CASE
                       WHEN o.remainingQuantity - :quantity = 0
                       THEN com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.SOLD_OUT
                       ELSE o.offeringStatus
                   END,
               o.updatedAt = CURRENT_TIMESTAMP,
               o.updatedBy = :userId
         WHERE o.offeringId = :offeringId
           AND o.isDeleted = false
           AND o.offeringStatus =
               com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.OPEN
           AND o.startAt <= CURRENT_TIMESTAMP
           AND o.endAt > CURRENT_TIMESTAMP
           AND o.remainingQuantity >= :quantity
        """)
    int reserveQuantity(
            @Param("offeringId") UUID offeringId,
            @Param("quantity") Long quantity,
            @Param("userId") UUID userId
    );

    /**
     * 시작 시간이 도래한 'SCHEDULED 공모를 OPEN'으로 일괄 전환한다.
     *
     * 스케줄러에서 주기적으로 호출하며,
     * 상태가 SCHEDULED이고 현재 시각이 모집 기간(startAt <= now < endAt)에 포함되며
     * 삭제되지 않은 공모만 OPEN으로 전환한다.
     * SCHEDULED → OPEN
     *
     * @return OPEN으로 전환된 공모 수
     *
     * TODO: PostgreSQL 기반 DB 통합 테스트 추가
     * - JPQL Bulk Update가 실제 PostgreSQL에서 정상 실행되는지 검증
     * - SCHEDULED + startAt <= now + endAt > now → OPEN 전환 검증
     * - SCHEDULED + endAt <= now → OPEN으로 전환되지 않는지 검증
     * - 미래 startAt / 다른 상태 / 삭제 공모가 변경되지 않는지 검증
     * - updatedAt / updatedBy(SYSTEM_USER_ID) 갱신 검증
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Offering o
           SET o.offeringStatus =
                   com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.OPEN,
               o.updatedAt = CURRENT_TIMESTAMP,
               o.updatedBy = :systemUserId
         WHERE o.offeringStatus =
                   com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.SCHEDULED
           AND o.startAt <= CURRENT_TIMESTAMP
           AND o.endAt > CURRENT_TIMESTAMP
           AND o.isDeleted = false
    """)
    int openScheduledOfferings(
            @Param("systemUserId") UUID systemUserId
    );

    /**
     * '모집 종료 시간이 도래한 SOLD_OUT 공모'를 'CLOSED로 일괄 전환'한다.
     *
     * 모집 기간 중 전량 소진되어 SOLD_OUT 상태가 된 공모 중
     * endAt이 현재 시각에 도달한 삭제되지 않은 공모만 CLOSED로 전환한다.
     * SOLD_OUT → CLOSED
     *
     * @return CLOSED로 전환된 공모 수
     *
     * TODO: PostgreSQL 기반 DB 통합 테스트 추가
     * - SOLD_OUT + endAt <= now → CLOSED 전환 검증
     * - SOLD_OUT + 미래 endAt → 상태 유지 검증
     * - 다른 상태 / 삭제 공모가 변경되지 않는지 검증
     * - updatedAt / updatedBy(SYSTEM_USER_ID) 갱신 검증
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Offering o
               SET o.offeringStatus =
                       com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.CLOSED,
                   o.updatedAt = CURRENT_TIMESTAMP,
                   o.updatedBy = :systemUserId
             WHERE o.offeringStatus =
                       com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.SOLD_OUT
               AND o.endAt <= CURRENT_TIMESTAMP
               AND o.isDeleted = false
            """)
    int closeSoldOutOfferings(
            @Param("systemUserId") UUID systemUserId
    );
}