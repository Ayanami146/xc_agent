package com.xc.agent.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xc.agent.config.AgentProperties;
import com.xc.agent.model.dto.internal.InternalAiDTOs;
import com.xc.agent.service.InternalAiService;
import com.xc.agent.service.ai.InternalAiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用 JDK 本地 HTTP Server 验证真实请求头、JSON 和 SSE 解析，不连接外部 Agent。 */
class InternalAiServiceImplTest {
    private HttpServer server;
    private InternalAiService service;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> internalToken;
    private AtomicReference<String> requestProtocol;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requestBody = new AtomicReference<>();
        internalToken = new AtomicReference<>();
        requestProtocol = new AtomicReference<>();
        AgentProperties properties = new AgentProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/ai/v1",
                "test-internal-token",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(6),
                "default",
                List.of("default"),
                1024);
        service = new InternalAiServiceImpl(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper(),
                properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsCamelCaseRequestAndParsesTerminalSse() {
        server.createContext("/internal/ai/v1/chat/stream", exchange -> {
            captureRequest(exchange);
            String sse = """
                    event: meta
                    data: {"event":"meta","requestId":11,"sequence":1,"payload":{"sessionId":22}}

                    event: delta
                    data: {"event":"delta","requestId":11,"sequence":2,"payload":{"content":"回答"}}

                    event: done
                    data: {"event":"done","requestId":11,"sequence":3,"payload":{"finishReason":"stop"}}

                    """;
            respond(exchange, 200, sse);
        });
        server.start();

        List<InternalAiService.AgentEvent> events = new ArrayList<>();
        service.stream(agentRequest(), events::add);

        assertThat(events).extracting(InternalAiService.AgentEvent::event)
                .containsExactly("meta", "delta", "done");
        assertThat(events.get(1).payload().path("content").asText()).isEqualTo("回答");
        assertThat(internalToken.get()).isEqualTo("test-internal-token");
        assertThat(requestProtocol.get()).isEqualTo("HTTP/1.1");
        assertThat(requestBody.get()).contains(
                "\"requestId\":11", "\"sessionId\":22", "\"userId\":33",
                "\"modelRoute\":\"default\"", "\"knowledgeBaseIds\":[\"default\"]");
    }

    @Test
    void rejectsStreamWithoutDoneOrError() {
        server.createContext("/internal/ai/v1/chat/stream", exchange -> respond(
                exchange, 200,
                "event: delta\ndata: {\"event\":\"delta\",\"payload\":{\"content\":\"半条\"}}\n\n"));
        server.start();

        assertThatThrownBy(() -> service.stream(agentRequest(), ignored -> { }))
                .isInstanceOf(InternalAiException.class)
                .extracting("code")
                .isEqualTo("AGENT_STREAM_INTERRUPTED");
    }

    @Test
    void mapsAgentValidationFailureToActionableError() {
        server.createContext("/internal/ai/v1/chat/stream", exchange -> respond(
                exchange, 422,
                """
                        {"detail":[{"type":"missing","loc":["body","policy"],
                        "msg":"Field required","input":{"message":"不应进入日志"}}]}
                        """));
        server.start();

        assertThatThrownBy(() -> service.stream(agentRequest(), ignored -> { }))
                .isInstanceOf(InternalAiException.class)
                .extracting("code")
                .isEqualTo("AGENT_REQUEST_INVALID");
    }

    @Test
    void callsAgentCancelEndpointWithToken() {
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/internal/ai/v1/chat/requests/11/cancel", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            internalToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            respond(exchange, 200, "{}");
        });
        server.start();

        service.cancel(11L);

        assertThat(path.get()).isEqualTo("/internal/ai/v1/chat/requests/11/cancel");
        assertThat(internalToken.get()).isEqualTo("test-internal-token");
    }

    private InternalAiDTOs.AgentChatStreamDTO agentRequest() {
        return new InternalAiDTOs.AgentChatStreamDTO(
                11L, 22L, 33L, "问题", null,
                new InternalAiDTOs.AgentPolicyDTO(
                        "default", List.of("default"), false, 1024));
    }

    private void captureRequest(HttpExchange exchange) throws IOException {
        requestProtocol.set(exchange.getProtocol());
        requestBody.set(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        internalToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
