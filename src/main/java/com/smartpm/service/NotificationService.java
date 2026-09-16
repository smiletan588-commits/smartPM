package com.smartpm.service;

import com.smartpm.entity.Notification;

import java.util.List;

public interface NotificationService {
    List<Notification> list(Boolean unreadOnly);
    long unreadCount();
    void markRead(Long id);
    void markAllRead();
    void create(Long userId, Long projectId, Long taskId, String type, String title, String content, String dedupeKey);
    void notifyAssignment(Long projectId, Long taskId, Long assigneeId, String taskTitle);
}
