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

class SlackDirectMessageSenderTest {

    private HttpServer server;
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile String capturedAuthHeader;
    private volatile String capturedRequestBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat.postMessage", exchange -> {
            capturedAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            capturedRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

    private SlackDirectMessageSender sender() {
        String url = "http://localhost:" + server.getAddress().getPort() + "/chat.postMessage";
        return new SlackDirectMessageSender("xoxb-test-token", url);
    }

    @Test
    @DisplayName("ok:true 응답이면 성공 결과를 반환하고, Bearer 토큰과 channel/text를 담아 보낸다")
    void send_okResponse_returnsSuccessAndSendsExpectedRequest() {
        responseStatus = 200;
        responseBody = "{\"ok\":true}";

        SlackSendResult result = sender().send("U123", "제목", "내용");

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
        assertThat(capturedAuthHeader).isEqualTo("Bearer xoxb-test-token");
        assertThat(capturedRequestBody).contains("\"channel\":\"U123\"");
        assertThat(capturedRequestBody).contains("제목").contains("내용");
    }

    @Test
    @DisplayName("HTTP는 200이지만 ok:false면 SlackSendFailedException을 던진다")
    void send_okFalse_throwsSlackSendFailedException() {
        responseStatus = 200;
        responseBody = "{\"ok\":false,\"error\":\"channel_not_found\"}";

        assertThatThrownBy(() -> sender().send("U123", "제목", "내용"))
                .isInstanceOf(SlackSendFailedException.class)
                .hasMessageContaining("channel_not_found");
    }

    @Test
    @DisplayName("Slack이 5xx를 반환하면 RestClient가 던지는 예외가 그대로 전파된다")
    void send_serverError_propagatesException() {
        responseStatus = 500;
        responseBody = "{\"ok\":false,\"error\":\"internal_error\"}";

        assertThatThrownBy(() -> sender().send("U123", "제목", "내용"))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    @DisplayName("재시도가 모두 소진되면 fallback이 예외를 실패 결과로 변환한다")
    void sendFallback_convertsThrowableToFailResult() {
        SlackDirectMessageSender sender = new SlackDirectMessageSender("xoxb-test-token", "http://localhost:1");
        RuntimeException cause = new IllegalStateException("boom");

        SlackSendResult result = ReflectionTestUtils.invokeMethod(
                sender, "sendFallback", "U123", "제목", "내용", cause
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("IllegalStateException:boom");
    }
}
