package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.common.websocket.TaskWebSocketHandler;
import com.smartpm.entity.Notification;
import com.smartpm.entity.Project;
import com.smartpm.entity.Task;
import com.smartpm.entity.TaskDependency;
import com.smartpm.mapper.NotificationMapper;
import com.smartpm.mapper.ProjectMapper;
import com.smartpm.mapper.TaskDependencyMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.service.NotificationService;
import com.smartpm.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationMapper notificationMapper;
    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final ProjectMapper projectMapper;
    private final TaskWebSocketHandler webSocketHandler;
    private final EmailNotificationService emailNotificationService;

    @Override
    public List<Notification> list(Boolean unreadOnly) {
        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, UserHolder.getUserId())
                .orderByDesc(Notification::getCreatedAt).last("LIMIT 100");
        if (Boolean.TRUE.equals(unreadOnly)) query.eq(Notification::getIsRead, false);
        return notificationMapper.selectList(query);
    }

    @Override
    public long unreadCount() {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, UserHolder.getUserId()).eq(Notification::getIsRead, false));
    }

    @Override
    public void markRead(Long id) {
        Notification item = notificationMapper.selectById(id);
        if (item == null || !item.getUserId().equals(UserHolder.getUserId())) throw new BusinessException("通知不存在");
        item.setIsRead(true);
        item.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(item);
    }

    @Override
    public void markAllRead() {
        List<Notification> items = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, UserHolder.getUserId()).eq(Notification::getIsRead, false));
        LocalDateTime now = LocalDateTime.now();
        items.forEach(item -> { item.setIsRead(true); item.setReadAt(now); notificationMapper.updateById(item); });
    }

    @Override
    public void create(Long userId, Long projectId, Long taskId, String type, String title, String content, String dedupeKey) {
        if (userId == null) return;
        Notification item = new Notification();
        item.setUserId(userId);
        item.setProjectId(projectId);
        item.setTaskId(taskId);
        item.setType(type);
        item.setTitle(title);
        item.setContent(content);
        item.setIsRead(false);
        item.setDedupeKey(dedupeKey);
        item.setCreatedAt(LocalDateTime.now());
        try { notificationMapper.insert(item); }
        catch (DuplicateKeyException ignored) { return; }
        emailNotificationService.enqueue(item);
        webSocketHandler.notifyUser(userId, "{\"type\":\"NOTIFICATION_UPDATED\"}");
    }

    @Override
    public void notifyAssignment(Long projectId, Long taskId, Long assigneeId, String taskTitle) {
        create(assigneeId, projectId, taskId, "TASK_ASSIGNED", "你收到一个新任务", taskTitle,
                "assignment:" + taskId + ":" + assigneeId + ":" + LocalDate.now());
    }

    @Scheduled(cron = "0 0 * * * *")
    public void createDeadlineAndBlockedNotifications() {
        LocalDate today = LocalDate.now();
        List<Long> activeProjectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .isNull(Project::getDeletedAt)).stream().map(Project::getId).toList();
        if (activeProjectIds.isEmpty()) return;
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getProjectId, activeProjectIds).ne(Task::getStatus, "DONE")
                .isNull(Task::getDeletedAt).isNotNull(Task::getAssigneeId));
        for (Task task : tasks) {
            if (task.getDueDate() != null && task.getDueDate().isBefore(today)) {
                create(task.getAssigneeId(), task.getProjectId(), task.getId(), "TASK_OVERDUE", "任务已逾期",
                        task.getTitle(), "overdue:" + task.getId() + ":" + today);
            } else if (task.getDueDate() != null && !task.getDueDate().isAfter(today.plusDays(3))) {
                create(task.getAssigneeId(), task.getProjectId(), task.getId(), "DUE_SOON", "任务即将到期",
                        task.getTitle() + " · " + task.getDueDate(), "due:" + task.getId() + ":" + today);
            }
            List<TaskDependency> dependencies = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                    .eq(TaskDependency::getTaskId, task.getId()));
            boolean blocked = dependencies.stream().map(TaskDependency::getPrerequisiteTaskId).map(taskMapper::selectById)
                    .anyMatch(item -> item == null || !"DONE".equals(item.getStatus()));
            if (blocked) create(task.getAssigneeId(), task.getProjectId(), task.getId(), "TASK_BLOCKED", "任务被前置工作阻塞",
                    task.getTitle(), "blocked:" + task.getId() + ":" + today);
        }
    }
}
