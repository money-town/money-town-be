package com.moneykk.moneytown.analysis.notification.infrastructure.slack;

import com.moneykk.moneytown.analysis.notification.command.dto.response.SlackApiResponse;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SlackDirectMessageSender {

    private final RestClient restClient;
    private final String botToken;
    private final String postMessageUrl;

    public SlackDirectMessageSender(
            @Value("${notification.slack.bot-token}") String botToken,
            @Value("${notification.slack.api-base-url:https://slack.com/api/chat.postMessage}") String postMessageUrl){
        this.botToken = botToken;
        this.postMessageUrl = postMessageUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Retry(name = "slackNotification", fallbackMethod = "sendFallback")
    public SlackSendResult send(String slackUserId, String title, String message){
        String text = "*" + title + "*\n" + message;

        SlackApiResponse response = restClient.post()
                .uri(postMessageUrl)
                .header("Authorization", "Bearer " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", slackUserId, "text", text))
                .retrieve()
                .body(SlackApiResponse.class);

        if(response != null && response.ok()){
            return SlackSendResult.ok();
        }
        String error = response != null ? response.error() : "empty_response";
        throw new SlackSendFailedException("Slack DM 응답 비정상: " + error);
    }

    private SlackSendResult sendFallback(String slackUserId, String title, String message, Throwable t){
        return SlackSendResult.fail(t.getClass().getSimpleName() + ":" + t.getMessage());
    }
}
