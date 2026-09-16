package com.smartpm.service.impl;

import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.AiOperationLog;
import com.smartpm.mapper.AiOperationLogMapper;
import com.smartpm.service.AiOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiOperationLogServiceImpl implements AiOperationLogService {
    private final AiOperationLogMapper mapper;
    @Value("${ai.model:unknown}")
    private String model;

    @Override
    public AiOperationLog start(String scene, Long projectId, Long taskId) {
        AiOperationLog log = new AiOperationLog();
        log.setUserId(UserHolder.getUserId());
        log.setProjectId(projectId);
        log.setTaskId(taskId);
        log.setScene(scene);
        log.setModel(model);
        log.setPromptVersion("graduate-v1");
        log.setGeneratedCount(0);
        log.setApplied(false);
        log.setModifiedCount(0);
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
        return log;
    }

    @Override
    public void succeed(AiOperationLog log, int generatedCount, boolean applied) {
        log.setSuccess(true);
        log.setGeneratedCount(Math.max(0, generatedCount));
        log.setApplied(applied);
        finish(log);
    }

    @Override
    public void fail(AiOperationLog log, Throwable error) {
        log.setSuccess(false);
        String message = error == null ? "未知错误" : error.getMessage();
        log.setErrorMessage(message == null ? "未知错误" : message.substring(0, Math.min(500, message.length())));
        finish(log);
    }

    @Override
    public void markApplied(Long operationId, int modifiedCount) {
        AiOperationLog log = requireOwned(operationId);
        log.setApplied(true);
        log.setModifiedCount(Math.max(0, modifiedCount));
        mapper.updateById(log);
    }

    @Override
    public void feedback(Long operationId, Integer rating, String feedback) {
        AiOperationLog log = requireOwned(operationId);
        if (rating == null || rating < 1 || rating > 5) throw new BusinessException("评分应为 1 到 5");
        log.setRating(rating);
        log.setFeedback(feedback == null ? null : feedback.trim());
        mapper.updateById(log);
    }

    private AiOperationLog requireOwned(Long id) {
        AiOperationLog log = mapper.selectById(id);
        if (log == null || !log.getUserId().equals(UserHolder.getUserId())) throw new BusinessException("AI 操作记录不存在");
        return log;
    }

    private void finish(AiOperationLog log) {
        LocalDateTime completedAt = LocalDateTime.now();
        log.setCompletedAt(completedAt);
        log.setDurationMs(Duration.between(log.getCreatedAt(), completedAt).toMillis());
        mapper.updateById(log);
    }
}
