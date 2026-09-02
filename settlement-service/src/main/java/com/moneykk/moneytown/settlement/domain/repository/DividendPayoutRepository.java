package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, UUID> {

    Optional<DividendPayout> findByIdAndIsDeletedFalse(UUID id);

    List<DividendPayout> findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus status, Instant threshold);

    List<DividendPayout> findBySettlementBatchIdAndStatusAndIsDeletedFalse(UUID settlementBatchId, PayoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DividendPayout> findBySettlementBatchIdAndStatusInAndIsDeletedFalse(UUID settlementBatchId, List<PayoutStatus> statuses);

    List<DividendPayout> findBySettlementBatchIdAndIsDeletedFalse(UUID settlementBatchId);

    Page<DividendPayout> findBySettlementBatchIdAndIsDeletedFalse(UUID settlementBatchId, Pageable pageable);

    Page<DividendPayout> findBySettlementBatchIdAndStatusAndIsDeletedFalse(UUID settlementBatchId, PayoutStatus status, Pageable pageable);

    @Query("SELECT DISTINCT p.settlementBatchId FROM DividendPayout p WHERE p.status IN :statuses AND p.isDeleted = false")
    List<UUID> findDistinctSettlementBatchIdByStatusIn(@Param("statuses") List<PayoutStatus> statuses);

    @Query("SELECT p.status AS status, COUNT(p) AS count FROM DividendPayout p " +
            "WHERE p.settlementBatchId = :settlementBatchId AND p.isDeleted = false GROUP BY p.status")
    List<PayoutStatusCount> countByStatusGrouped(@Param("settlementBatchId") UUID settlementBatchId);

    //TODO: 가독성 개선
    @Query(value = "SELECT p.id AS dividendPayoutId, b.assetId AS assetId, p.settlementBatchId AS settlementBatchId, " +
            "b.recordDate AS recordDate, p.shareRatio AS shareRatio, p.amount AS amount, " +
            "p.status AS status, p.updatedAt AS updatedAt " +
            "FROM DividendPayout p JOIN SettlementBatch b ON b.id = p.settlementBatchId " +
            "WHERE p.investorId = :investorId AND (:assetId IS NULL OR b.assetId = :assetId) " +
            "AND p.isDeleted = false AND b.isDeleted = false " +
            "ORDER BY p.updatedAt DESC, p.id ASC",
            countQuery = "SELECT COUNT(p) FROM DividendPayout p JOIN SettlementBatch b ON b.id = p.settlementBatchId " +
                    "WHERE p.investorId = :investorId AND (:assetId IS NULL OR b.assetId = :assetId) " +
                    "AND p.isDeleted = false AND b.isDeleted = false")
    Page<MyDividendPayoutRow> findMyDividendPayouts(@Param("investorId") UUID investorId,
                                                      @Param("assetId") UUID assetId,
                                                      Pageable pageable);

    interface PayoutStatusCount {
        PayoutStatus getStatus();

        long getCount();
    }

    interface MyDividendPayoutRow {
        UUID getDividendPayoutId();

        UUID getAssetId();

        UUID getSettlementBatchId();

        LocalDate getRecordDate();

        BigDecimal getShareRatio();

        Long getAmount();

        PayoutStatus getStatus();

        Instant getUpdatedAt();
    }
}