package com.moneykk.moneytown.settlement.command.dto;

import java.util.List;
import java.util.UUID;

public record FinalSettlementRetryRequest(
        List<UUID> finalSettlementPayoutIds
) {
}