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

    /**
     * 선착순 청약 수량을 원자적으로 확보한다.
     *
     * OPEN 상태이고 모집 기간 내이며 잔여 수량이 충분한 경우에만 차감한다.
     * 차감 결과 잔여 수량이 0이 되면 SOLD_OUT 상태로 변경한다.
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
}