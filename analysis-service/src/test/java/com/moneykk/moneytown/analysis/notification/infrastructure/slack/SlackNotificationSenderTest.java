package com.moneykk.moneytown.analysis.notification.infrastructure.slack;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackNotificationSenderTest {

    private HttpServer server;
    private volatile int responseStatus;
    private volatile String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/webhook", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SlackNotificationSender sender() {
        String url = "http://localhost:" + server.getAddress().getPort() + "/webhook";
        return new SlackNotificationSender(url);
    }

    @Test
    @DisplayName("응답이 200이고 본문이 ok면 성공 결과를 반환한다")
    void send_okResponse_returnsSuccess() {
        responseStatus = 200;
        responseBody = "ok";

        SlackSendResult result = sender().send("제목", "내용");

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("응답이 200이지만 본문이 ok가 아니면 SlackSendFailedException을 던진다")
    void send_unexpectedBody_throwsSlackSendFailedException() {
        responseStatus = 200;
        responseBody = "invalid_payload";

        assertThatThrownBy(() -> sender().send("제목", "내용"))
                .isInstanceOf(SlackSendFailedException.class)
                .hasMessageContaining("Slack 응답 비정상");
    }

    @Test
    @DisplayName("Slack이 5xx를 반환하면 RestClient가 던지는 예외가 그대로 전파된다")
    void send_serverError_propagatesException() {
        responseStatus = 500;
        responseBody = "error";

        assertThatThrownBy(() -> sender().send("제목", "내용"))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    @DisplayName("재시도가 모두 소진되면 fallback이 예외를 실패 결과로 변환한다")
    void sendFallback_convertsThrowableToFailResult() {
        SlackNotificationSender sender = new SlackNotificationSender("http://localhost:1");
        RuntimeException cause = new IllegalStateException("boom");

        SlackSendResult result = ReflectionTestUtils.invokeMethod(
                sender, "sendFallback", "제목", "내용", cause
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("IllegalStateException:boom");
    }
}
