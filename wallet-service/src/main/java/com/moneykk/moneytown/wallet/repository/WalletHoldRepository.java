package com.moneykk.moneytown.wallet.repository;

import com.moneykk.moneytown.wallet.entity.WalletHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {

    Optional<WalletHold> findBySubscriptionId(UUID subscriptionId);

    List<WalletHold> findByWalletId(Long walletId);
}
