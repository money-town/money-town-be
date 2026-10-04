package com.moneykk.moneytown.offering.subscription.command.dto.response;

public record SubscriptionRequestAcceptedResult(
        SubscriptionRequestAcceptedResponse response,
        boolean replayed
) {
}
