package com.moneykk.moneytown.offering.subscription.command.application;

public class SubscriptionRequestRetryableException extends RuntimeException {

    public SubscriptionRequestRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
