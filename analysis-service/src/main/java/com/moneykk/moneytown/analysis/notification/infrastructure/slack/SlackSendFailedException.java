package com.moneykk.moneytown.analysis.notification.infrastructure.slack;

public class SlackSendFailedException extends RuntimeException{
    public SlackSendFailedException(String message){
        super(message);
    }
}
