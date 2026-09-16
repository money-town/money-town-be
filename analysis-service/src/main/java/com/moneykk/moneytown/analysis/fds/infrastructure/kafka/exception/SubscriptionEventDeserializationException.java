package com.moneykk.moneytown.analysis.fds.infrastructure.kafka.exception;

public class SubscriptionEventDeserializationException extends RuntimeException{
    public SubscriptionEventDeserializationException(String message, Throwable cause){
        super(message, cause);
    }
}
