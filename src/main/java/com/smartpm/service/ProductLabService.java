package com.smartpm.service;

import com.smartpm.dto.ProductArtifactApplyDTO;
import com.smartpm.dto.ProductArtifactDTO;
import com.smartpm.dto.ProductConversationCreateDTO;
import com.smartpm.dto.ProductMessageDTO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface ProductLabService {
    List<Map<String, Object>> conversations(Long projectId);
    Map<String, Object> createConversation(Long projectId, ProductConversationCreateDTO dto);
    Map<String, Object> conversation(Long projectId, Long conversationId);
    Flux<String> streamMessage(Long projectId, Long conversationId, ProductMessageDTO dto);
    List<Map<String, Object>> artifacts(Long projectId, Long conversationId);
    Map<String, Object> artifact(Long projectId, Long artifactId);
    Map<String, Object> createArtifact(Long projectId, ProductArtifactDTO dto);
    Map<String, Object> updateArtifact(Long projectId, Long artifactId, ProductArtifactDTO dto);
    Map<String, Object> publishArtifact(Long projectId, Long artifactId);
    Map<String, Object> previewApply(Long projectId, Long artifactId, ProductArtifactApplyDTO dto);
    Map<String, Object> applyArtifact(Long projectId, Long artifactId, ProductArtifactApplyDTO dto);
}
