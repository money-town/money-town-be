package com.moneykk.moneytown.offering.offering.domain.repository;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OfferingRepository extends JpaRepository<Offering, UUID> {

    Optional<Offering> findByOfferingIdAndIsDeletedFalse(UUID offeringId);
}