package com.moneykk.moneytown.wallet.repository;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    // 커서(createdAt, id) 기준 keyset 페이지네이션. Slice라 COUNT(*) 없이 size+1개만 가져와 hasNext를 판단한다.
    // cast(...)는 Postgres가 null 파라미터의 타입을 못 정해서 에러 나는 걸 막는 용도.
    @Query("""
            select t from WalletTransaction t
            where t.walletId = :walletId
              and (:type is null or t.type = :type)
              and (cast(:from as timestamp) is null or t.createdAt >= :from)
              and (cast(:to as timestamp) is null or t.createdAt <= :to)
              and (cast(:cursorCreatedAt as timestamp) is null
                   or t.createdAt < :cursorCreatedAt
                   or (t.createdAt = :cursorCreatedAt and t.id < :cursorId))
            order by t.createdAt desc, t.id desc
            """)
    Slice<WalletTransaction> findByWalletId(@Param("walletId") Long walletId,
                                             @Param("type") WalletTransactionType type,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to,
                                             @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                             @Param("cursorId") Long cursorId,
                                             Pageable pageable);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    // 지갑별 마지막 거래의 balance_after = 그 지갑의 기대 잔액.
    @Query(value = """
            SELECT DISTINCT ON (wallet_id) wallet_id AS "walletId", balance_after AS "balanceAfter"
            FROM p_wallet_transactions
            ORDER BY wallet_id, transaction_id DESC
            """, nativeQuery = true)
    List<WalletLedgerSnapshot> findLatestBalanceAfterPerWallet();
}
