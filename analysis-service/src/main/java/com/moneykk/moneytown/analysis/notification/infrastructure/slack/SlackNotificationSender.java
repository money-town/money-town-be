package com.moneykk.moneytown.analysis.notification.infrastructure.slack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SlackNotificationSender {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackNotificationSender(@Value("${notification.slack.webhook-url}") String webhookUrl){
        this.webhookUrl = webhookUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public SlackSendResult send(String title, String message){
        String text = "*" + title + "*\n" + message;
        try{
            ResponseEntity<String> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toEntity(String.class);

            String body = response.getBody() == null ? "" : response.getBody().trim();
            if(response.getStatusCode().is2xxSuccessful() && "ok".equals(body)){
                return SlackSendResult.ok();
            }
            return SlackSendResult.fail("Slack 응답 비정상: " + response.getStatusCode() + "/" + body);
        }catch (Exception e){
            return SlackSendResult.fail(e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }
}
