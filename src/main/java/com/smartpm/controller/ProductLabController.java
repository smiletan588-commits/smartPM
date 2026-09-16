package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.dto.ProductArtifactApplyDTO;
import com.smartpm.dto.ProductArtifactDTO;
import com.smartpm.dto.ProductConversationCreateDTO;
import com.smartpm.dto.ProductMessageDTO;
import com.smartpm.service.ProductLabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/project/{projectId}/product-lab")
@RequiredArgsConstructor
public class ProductLabController {
    private final ProductLabService productLabService;

    @GetMapping("/conversations")
    public R<List<Map<String, Object>>> conversations(@PathVariable Long projectId) {
        return R.ok(productLabService.conversations(projectId));
    }

    @PostMapping("/conversations")
    public R<Map<String, Object>> createConversation(@PathVariable Long projectId,
                                                      @Valid @RequestBody ProductConversationCreateDTO dto) {
        return R.ok(productLabService.createConversation(projectId, dto));
    }

    @GetMapping("/conversations/{conversationId}")
    public R<Map<String, Object>> conversation(@PathVariable Long projectId, @PathVariable Long conversationId) {
        return R.ok(productLabService.conversation(projectId, conversationId));
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long projectId, @PathVariable Long conversationId,
                             @Valid @RequestBody ProductMessageDTO dto) {
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicReference<Disposable> active = new AtomicReference<>();
        Runnable cancel = () -> {
            Disposable disposable = active.getAndSet(null);
            if (disposable != null && !disposable.isDisposed()) disposable.dispose();
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> { cancel.run(); emitter.complete(); });
        emitter.onError(error -> cancel.run());
        Disposable subscription = productLabService.streamMessage(projectId, conversationId, dto).subscribe(
                chunk -> send(emitter, "delta", chunk),
                error -> {
                    send(emitter, "error", error.getMessage() == null ? "生成失败" : error.getMessage());
                    emitter.complete();
                },
                () -> {
                    send(emitter, "done", "complete");
                    emitter.complete();
                });
        active.set(subscription);
        return emitter;
    }

    @GetMapping("/artifacts")
    public R<List<Map<String, Object>>> artifacts(@PathVariable Long projectId,
                                                   @RequestParam(required = false) Long conversationId) {
        return R.ok(productLabService.artifacts(projectId, conversationId));
    }

    @GetMapping("/artifacts/{artifactId}")
    public R<Map<String, Object>> artifact(@PathVariable Long projectId, @PathVariable Long artifactId) {
        return R.ok(productLabService.artifact(projectId, artifactId));
    }

    @PostMapping("/artifacts")
    public R<Map<String, Object>> createArtifact(@PathVariable Long projectId,
                                                  @Valid @RequestBody ProductArtifactDTO dto) {
        return R.ok(productLabService.createArtifact(projectId, dto));
    }

    @PutMapping("/artifacts/{artifactId}")
    public R<Map<String, Object>> updateArtifact(@PathVariable Long projectId, @PathVariable Long artifactId,
                                                  @Valid @RequestBody ProductArtifactDTO dto) {
        return R.ok(productLabService.updateArtifact(projectId, artifactId, dto));
    }

    @PostMapping("/artifacts/{artifactId}/publish")
    public R<Map<String, Object>> publishArtifact(@PathVariable Long projectId, @PathVariable Long artifactId) {
        return R.ok(productLabService.publishArtifact(projectId, artifactId));
    }

    @PostMapping("/artifacts/{artifactId}/apply/preview")
    public R<Map<String, Object>> previewApply(@PathVariable Long projectId, @PathVariable Long artifactId,
                                                @Valid @RequestBody ProductArtifactApplyDTO dto) {
        return R.ok(productLabService.previewApply(projectId, artifactId, dto));
    }

    @PostMapping("/artifacts/{artifactId}/apply")
    public R<Map<String, Object>> applyArtifact(@PathVariable Long projectId, @PathVariable Long artifactId,
                                                @Valid @RequestBody ProductArtifactApplyDTO dto) {
        return R.ok(productLabService.applyArtifact(projectId, artifactId, dto));
    }

    private void send(SseEmitter emitter, String event, String data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException ignored) { emitter.complete(); }
    }
}
