package com.moneykk.moneytown.wallet.repository;

import com.moneykk.moneytown.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(UUID userId);

    // 잔액을 바꾸는 트랜잭션(입출금/동결 등)은 반드시 이 메서드로 조회해서 락을 잡아야 함.
    // 락은 이 메서드를 호출한 트랜잭션이 끝날 때(commit/rollback) 풀리므로,
    // 서비스 메서드에 @Transactional이 걸려 있어야 의미가 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    // 보상 처리(RELEASE/REFUND)처럼 WalletHold.walletId로 지갑을 먼저 찾아야 하는 흐름에서 씀.
    // JpaRepository가 기본 제공하는 findById()는 락이 걸리지 않는다.
    // 잔액을 바꿀 거면 이걸 사용 해야함.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :walletId")
    Optional<Wallet> findByIdForUpdate(@Param("walletId") Long walletId);
}
