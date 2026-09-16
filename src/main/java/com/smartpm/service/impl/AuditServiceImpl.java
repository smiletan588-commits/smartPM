package com.smartpm.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public void record(Long projectId, String category, String action, String targetType,
                       Long targetId, String summary, Map<String, ?> metadata) {
        try {
            jdbc.update("INSERT INTO pm_audit_event(actor_id, project_id, category, action, target_type, target_id, summary, metadata) VALUES (?,?,?,?,?,?,?,CAST(? AS JSON))",
                    UserHolder.getUserId(), projectId, category, action, targetType, targetId,
                    compact(summary, 500), json(metadata));
        } catch (RuntimeException e) {
            log.warn("审计事件写入失败 action={}: {}", action, e.getMessage());
        }
    }

    @Override
    public void recordLogin(Long userId, String username, boolean success, String reason) {
        try {
            jdbc.update("INSERT INTO pm_login_event(user_id, username, success, reason) VALUES (?,?,?,?)",
                    userId, compact(username == null ? "" : username, 64), success, compact(reason, 255));
        } catch (RuntimeException e) {
            log.warn("登录事件写入失败: {}", e.getMessage());
        }
    }

    private String json(Map<String, ?> metadata) {
        try { return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private String compact(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
