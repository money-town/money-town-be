package com.moneykk.moneytown.offering.offering.domain.repository;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OfferingRepository extends JpaRepository<Offering, UUID> {
}