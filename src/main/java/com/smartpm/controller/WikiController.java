package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.entity.Wiki;
import com.smartpm.entity.WikiVersion;
import com.smartpm.dto.WikiTaskLinksDTO;
import jakarta.validation.Valid;
import com.smartpm.service.AIService;
import com.smartpm.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiService wikiService;
    private final AIService aiService;

    @PostMapping("/create")
    public R<Wiki> create(@RequestParam Long projectId,
                          @RequestParam String title,
                          @RequestParam(required = false) String content) {
        Wiki wiki = wikiService.create(projectId, title, content);
        return R.ok(wiki);
    }

    @GetMapping("/list/{projectId}")
    public R<List<Map<String, Object>>> list(@PathVariable Long projectId,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long taskId) {
        List<Map<String, Object>> list = wikiService.listByProject(projectId, keyword, taskId);
        return R.ok(list);
    }

    @GetMapping("/{id}")
    public R<Wiki> getById(@PathVariable Long id) {
        Wiki wiki = wikiService.getById(id);
        return R.ok(wiki);
    }

    @PutMapping("/update")
    public R<Wiki> update(@RequestParam Long id,
                          @RequestParam(required = false) String title,
                          @RequestParam(required = false) String content) {
        Wiki wiki = wikiService.update(id, title, content);
        return R.ok(wiki);
    }

    @GetMapping("/{id}/versions")
    public R<List<WikiVersion>> versions(@PathVariable Long id) {
        return R.ok(wikiService.listVersions(id));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public R<Wiki> restore(@PathVariable Long id, @PathVariable Long versionId) {
        return R.ok(wikiService.restoreVersion(id, versionId));
    }

    @PutMapping("/{id}/task-links")
    public R<List<Long>> updateLinks(@PathVariable Long id, @Valid @RequestBody WikiTaskLinksDTO dto) {
        return R.ok(wikiService.updateTaskLinks(id, dto.getTaskIds()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        wikiService.delete(id);
        return R.ok();
    }

    /**
     * AI 写作协同 — SSE 流式推送。
     *
     * 重构要点：直接注入 AIService 构建 Prompt 并调用 streamChat()，
     * 与已验证可工作的 ProjectController#aiSummary 保持完全一致的
     * SseEmitter + Flux.subscribe() 模式，消除中间层差异。
     */
    @GetMapping(value = "/ai-copilot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiCopilot(@RequestParam String prompt,
                                @RequestParam String text) {
        log.info("[AI-Copilot] 收到请求: prompt={}, text长度={}", prompt, text.length());

        SseEmitter emitter = new SseEmitter(300_000L);

        // 构造 Prompt（与 WikiServiceImpl.buildCopilotPrompt 相同逻辑）
        String fullPrompt = "作为一名资深的文档编辑，请根据用户指令【" + prompt +
                "】，对以下文本进行处理，直接输出处理后的结果，不要带多余的解释：\n\n" + text;
        log.info("[AI-Copilot] 构造 Prompt 完成，长度={} 字符", fullPrompt.length());

        aiService.streamChat(fullPrompt).subscribe(
                chunk -> {
                    try {
                        log.info("[AI-Copilot] emitter.send() 发送 ({} 字符): {}",
                                chunk.length(),
                                chunk.length() > 60 ? chunk.substring(0, 60) + "..." : chunk);
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        log.warn("[AI-Copilot] SSE 发送失败，客户端可能已断开: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("[AI-Copilot] 异步流生成异常: {}", error.getMessage(), error);
                    try {
                        String errMsg = "[ERROR] " + (error.getMessage() != null
                                ? error.getMessage() : "未知错误");
                        emitter.send(SseEmitter.event().data(errMsg));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(error);
                },
                () -> {
                    log.info("[AI-Copilot] Flux onComplete → emitter.complete()");
                    emitter.complete();
                }
        );

        emitter.onTimeout(() -> {
            log.warn("[AI-Copilot] SSE 超时");
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.error("[AI-Copilot] SSE 连接异常", throwable);
        });

        return emitter;
    }
}
