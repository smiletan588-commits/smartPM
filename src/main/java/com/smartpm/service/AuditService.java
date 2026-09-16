package com.smartpm.service;

import java.util.Map;

public interface AuditService {
    void record(Long projectId, String category, String action, String targetType,
                Long targetId, String summary, Map<String, ?> metadata);
    void recordLogin(Long userId, String username, boolean success, String reason);
}
