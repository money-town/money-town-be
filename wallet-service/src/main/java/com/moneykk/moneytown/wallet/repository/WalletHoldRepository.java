package com.moneykk.moneytown.wallet.repository;

import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {

    Optional<WalletHold> findBySubscriptionId(UUID subscriptionId);

    // DEDUCT/UNHOLD/REFUND처럼 상태를 전이시키는 흐름에서 씀. 락 없이 조회하면 같은 hold를 겨냥한
    // 동시 요청이 서로 다른 트랜잭션에서 같은 상태를 읽고 중복 처리를 할 수 있음.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from WalletHold h where h.subscriptionId = :subscriptionId")
    Optional<WalletHold> findBySubscriptionIdForUpdate(@Param("subscriptionId") UUID subscriptionId);

    List<WalletHold> findByWalletId(Long walletId);

    boolean existsByWalletIdAndStatus(Long walletId, WalletHoldStatus status);
}
