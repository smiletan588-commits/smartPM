package com.smartpm.service;

import com.smartpm.entity.AiOperationLog;

public interface AiOperationLogService {
    AiOperationLog start(String scene, Long projectId, Long taskId);
    void succeed(AiOperationLog log, int generatedCount, boolean applied);
    void fail(AiOperationLog log, Throwable error);
    void markApplied(Long operationId, int modifiedCount);
    void feedback(Long operationId, Integer rating, String feedback);
}
