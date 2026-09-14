package com.moneykk.moneytown.wallet.repository;

import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    // GET /wallets/me/transactions는 type 쿼리 파라미터가 없을 수도 있어서(전체 조회),
    // type이 null이면 조건을 무시하도록 JPQL에서 직접 분기한다.
    @Query("""
            select t from WalletTransaction t
            where t.walletId = :walletId
              and (:type is null or t.type = :type)
            order by t.createdAt desc, t.id desc
            """)
    Page<WalletTransaction> findByWalletId(@Param("walletId") Long walletId,
                                            @Param("type") WalletTransactionType type,
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
