package com.moneykk.moneytown.offering.subscription.command.dto.response;

public record SubscriptionCreateResult(
        SubscriptionCreateResponse response,
        boolean replayed
) {

    public static SubscriptionCreateResult created(
            SubscriptionCreateResponse response
    ) {
        return new SubscriptionCreateResult(response, false);
    }

    public static SubscriptionCreateResult replayed(
            SubscriptionCreateResponse response
    ) {
        return new SubscriptionCreateResult(response, true);
    }
}