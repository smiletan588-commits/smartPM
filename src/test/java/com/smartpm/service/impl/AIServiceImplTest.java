package com.smartpm.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.common.config.AIConfigProperties;
import com.smartpm.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AIServiceImplTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicReference<String> finishReason = new AtomicReference<>("stop");
    private HttpServer server;
    private AIServiceImpl service;

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(mapper.readTree(exchange.getRequestBody()));
            String response = "{\"choices\":[{\"finish_reason\":\"" + finishReason.get()
                    + "\",\"message\":{\"content\":\"{\\\"tasks\\\":[]}\"}}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        AIConfigProperties config = new AIConfigProperties();
        config.setApiKey("test-key");
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setModel("deepseek-flash");
        service = new AIServiceImpl(config, WebClient.builder());
        service.init();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void planningCallRequestsJsonOutputWithoutChangingPlainChat() {
        assertEquals("{\"tasks\":[]}", service.chatJson("只返回 JSON"));
        assertEquals("json_object", request.get().path("response_format").path("type").asText());
        assertEquals(16384, request.get().path("max_tokens").asInt());
        assertEquals("deepseek-flash", request.get().path("model").asText());

        assertEquals("{\"tasks\":[]}", service.chat("普通聊天"));
        assertTrue(request.get().path("response_format").isMissingNode());
    }

    @Test
    void truncatedJsonIsNotAcceptedAsAPlanningDraft() {
        finishReason.set("length");
        BusinessException error = assertThrows(BusinessException.class, () -> service.chatJson("只返回 JSON"));
        assertTrue(error.getMessage().contains("输出超出长度限制"));
    }
}
