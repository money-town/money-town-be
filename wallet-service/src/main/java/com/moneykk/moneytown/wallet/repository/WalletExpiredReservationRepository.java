package com.moneykk.moneytown.wallet.repository;

import com.moneykk.moneytown.wallet.entity.WalletExpiredReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletExpiredReservationRepository extends JpaRepository<WalletExpiredReservation, UUID> {
}
