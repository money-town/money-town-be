package com.moneykk.moneytown.analysis.notification.infrastructure.slack;

public record SlackSendResult(boolean success, String errorMessage) {

    public static SlackSendResult ok(){
        return new SlackSendResult(true, null);
    }
    public static SlackSendResult fail(String errorMessage){
        return new SlackSendResult(false, errorMessage);
    }
}
