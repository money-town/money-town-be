package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfferingStatusTransitionService {

    private final OfferingRepository offeringRepository;

    @Transactional
    public int openScheduledOfferings() {
        return offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }
}