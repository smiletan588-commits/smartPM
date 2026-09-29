package com.smartpm.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.common.config.AIConfigProperties;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.service.AIService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {
    private static final int JSON_MAX_TOKENS = 16384;

    private final AIConfigProperties aiConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebClient webClient;
    private boolean apiKeyConfigured;

    @PostConstruct
    public void init() {
        String apiKey = resolveApiKey();
        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();
        this.webClient = webClientBuilder
                .baseUrl(aiConfig.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (apiKeyConfigured ? apiKey : ""))
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("[AI] 初始化完成: baseUrl={}, model={}, apiKeyConfigured={}",
                aiConfig.getBaseUrl(), aiConfig.getModel(), apiKeyConfigured);
    }

    @Override
    public Flux<String> streamChat(String prompt) {
        ensureApiKeyConfigured();
        if (prompt == null || prompt.isBlank()) {
            return Flux.error(new IllegalArgumentException("prompt 不能为空"));
        }

        Map<String, Object> body = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", true
        );

        log.info("[AI] 发起流式调用: model={}, promptLength={}", aiConfig.getModel(), prompt.length());

        // Spring WebFlux 的 text/event-stream 解码器已自动剥离 SSE 协议的
        // "data:" 前缀和换行符，每个 chunk 是纯 JSON (如 {"choices":[...]})。
        // 无需任何缓冲区或行分割处理。
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.info("[AI] SSE 订阅已建立"))
                .<String>handle((jsonChunk, sink) -> {
                    String content = extractContentFromJson(jsonChunk);
                    if (content != null && !content.isEmpty()) {
                        sink.next(content);
                    }
                })
                .doOnNext(content -> log.info("[AI] 发射 token ({} chars): {}",
                        content.length(),
                        content.length() > 60 ? content.substring(0, 60) + "..." : content))
                .doOnComplete(() -> log.info("[AI] 流式调用完成"))
                .doOnError(err -> log.error("[AI] 流式调用异常: {}", err.getMessage(), err));
    }

    /**
     * 从 SSE 解码后的纯 JSON chunk 中提取文本。
     * Spring WebFlux 已将 "data:" 前缀和 "\n" 剥离，chunk 直接是 JSON。
     * 兼容 content 和 reasoning_content 两种字段（DeepSeek 推理模型用后者）。
     */
    private String extractContentFromJson(String jsonChunk) {
        try {
            if (jsonChunk == null || jsonChunk.isBlank() || jsonChunk.contains("[DONE]")) {
                return null;
            }
            JsonNode root = objectMapper.readTree(jsonChunk);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) return null;
            JsonNode delta = choices.get(0).path("delta");
            if (delta.isEmpty() || delta.isMissingNode()) return null;

            // 优先取 content，为空则取 reasoning_content (DeepSeek 推理模型)
            JsonNode contentNode = delta.path("content");
            String text = (contentNode != null && !contentNode.isNull()) ? contentNode.asText() : null;
            if (text == null || text.isEmpty()) {
                JsonNode reasoningNode = delta.path("reasoning_content");
                text = (reasoningNode != null && !reasoningNode.isNull()) ? reasoningNode.asText() : null;
            }
            return (text != null && !text.isEmpty()) ? text : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String chat(String prompt) {
        return chat(prompt, false);
    }

    @Override
    public String chatJson(String prompt) {
        return chat(prompt, true);
    }

    private String chat(String prompt, boolean jsonMode) {
        ensureApiKeyConfigured();
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        Map<String, Object> body = jsonMode
                ? Map.of("model", aiConfig.getModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", false,
                        "response_format", Map.of("type", "json_object"),
                        "max_tokens", JSON_MAX_TOKENS)
                : Map.of("model", aiConfig.getModel(),
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "stream", false);

        log.info("[AI] 发起非流式调用: model={}, jsonMode={}, promptLength={}", aiConfig.getModel(), jsonMode, prompt.length());

        try {
            String response = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                if (jsonMode && "length".equals(choice.path("finish_reason").asText())) {
                    throw new BusinessException("AI 输出超出长度限制，请缩小规划范围后重试");
                }
                JsonNode content = choice.path("message").path("content");
                if (!content.isNull() && !content.isMissingNode()) {
                    String text = content.asText();
                    if (jsonMode && text.isBlank()) throw new BusinessException("AI 返回了空的规划内容，请重试");
                    log.info("[AI] 非流式响应: {} chars", text.length());
                    return text;
                }
            }
            log.warn("[AI] 非流式响应的 choices 为空");
            if (jsonMode) throw new BusinessException("AI 未返回规划内容，请重试");
            return "";
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] 非流式调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务调用失败，请确认后端已加载 AI 配置后重试");
        }
    }

    private void ensureApiKeyConfigured() {
        if (!apiKeyConfigured) {
            throw new BusinessException("AI 密钥未加载，请检查项目根目录 .env 后重启后端");
        }
    }

    /**
     * IDEA 的运行目录可能不是项目根目录。配置未绑定到 AI_API_KEY 时，
     * 从 user.dir 与编译产物目录向上查找项目根目录的 .env 作为本地开发兜底。
     */
    private String resolveApiKey() {
        if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank()) {
            return aiConfig.getApiKey().trim();
        }
        String fromWorkingDirectory = readApiKeyFromParents(Paths.get(System.getProperty("user.dir", ".")));
        if (fromWorkingDirectory != null) return fromWorkingDirectory;
        try {
            Path classPath = Paths.get(AIServiceImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return readApiKeyFromParents(Files.isDirectory(classPath) ? classPath : classPath.getParent());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readApiKeyFromParents(Path start) {
        Path current = start == null ? null : start.toAbsolutePath();
        for (int depth = 0; current != null && depth < 5; depth++, current = current.getParent()) {
            Path dotenv = current.resolve(".env");
            if (!Files.isRegularFile(dotenv)) continue;
            try {
                for (String line : Files.readAllLines(dotenv)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("AI_API_KEY=")) continue;
                    String value = trimmed.substring("AI_API_KEY=".length()).trim();
                    if ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!value.isBlank()) return value;
                }
            } catch (IOException ignored) {
                // 本地兜底文件不可读时继续尝试上级目录。
            }
        }
        return null;
    }
}
