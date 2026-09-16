package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.CommentCreateDTO;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.CollaborationService;
import com.smartpm.service.NotificationService;
import com.smartpm.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {
    private final TaskCommentMapper commentMapper;
    private final TaskActivityMapper activityMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public List<TaskComment> listComments(Long taskId) {
        Task task = requireTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), false);
        List<TaskComment> comments = commentMapper.selectList(new LambdaQueryWrapper<TaskComment>()
                .eq(TaskComment::getTaskId, taskId).isNull(TaskComment::getDeletedAt)
                .orderByAsc(TaskComment::getCreatedAt));
        enrichComments(comments);
        return comments;
    }

    @Override
    public TaskComment addComment(Long taskId, CommentCreateDTO dto) {
        Task task = requireTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), true);
        List<Long> mentions = dto.getMentionedUserIds() == null ? List.of() : dto.getMentionedUserIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        for (Long userId : mentions) assertProjectMember(task.getProjectId(), userId);

        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setProjectId(task.getProjectId());
        comment.setUserId(UserHolder.getUserId());
        comment.setContent(dto.getContent().trim());
        comment.setMentionedUserIds(mentions.stream().map(String::valueOf).collect(Collectors.joining(",")));
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        record(task.getProjectId(), taskId, "COMMENT_ADDED", "发表了任务评论", null, Map.of("commentId", comment.getId()));
        for (Long mentioned : mentions) {
            if (Objects.equals(mentioned, UserHolder.getUserId())) continue;
            notificationService.create(mentioned, task.getProjectId(), taskId, "COMMENT_MENTIONED",
                    "你在评论中被提及", task.getTitle(), "mention:" + comment.getId() + ":" + mentioned);
        }
        enrichComments(List.of(comment));
        return comment;
    }

    @Override
    public void deleteComment(Long taskId, Long commentId) {
        Task task = requireTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), true);
        TaskComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null || !Objects.equals(comment.getTaskId(), taskId)) {
            throw new BusinessException("评论不存在");
        }
        if (!Objects.equals(comment.getUserId(), UserHolder.getUserId())) {
            throw new BusinessException("只能删除自己的评论");
        }
        comment.setDeletedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        record(task.getProjectId(), taskId, "COMMENT_DELETED", "删除了任务评论", Map.of("commentId", commentId), null);
    }

    @Override
    public List<TaskActivity> listActivities(Long taskId) {
        Task task = requireTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), false);
        List<TaskActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<TaskActivity>()
                .eq(TaskActivity::getTaskId, taskId).orderByDesc(TaskActivity::getCreatedAt).last("LIMIT 100"));
        Set<Long> ids = activities.stream().map(TaskActivity::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                User::getId, u -> u.getNickname() == null ? u.getUsername() : u.getNickname()));
        activities.forEach(item -> item.setActorName(item.getActorId() == null ? "系统" : names.getOrDefault(item.getActorId(), "未知用户")));
        return activities;
    }

    @Override
    public void record(Long projectId, Long taskId, String actionType, String summary, Object before, Object after) {
        TaskActivity activity = new TaskActivity();
        activity.setProjectId(projectId);
        activity.setTaskId(taskId);
        activity.setActorId(UserHolder.get() == null ? null : UserHolder.getUserId());
        activity.setActionType(actionType);
        activity.setSummary(summary);
        activity.setBeforeJson(toJson(before));
        activity.setAfterJson(toJson(after));
        activity.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
    }

    private Task requireTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null) throw new BusinessException("任务不存在或已移入回收站");
        return task;
    }

    private void assertProjectMember(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project != null && Objects.equals(project.getCreatorId(), userId)) return;
        if (memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId)) == 0) {
            throw new BusinessException("提醒对象不是当前项目成员");
        }
    }

    private void enrichComments(List<TaskComment> comments) {
        Set<Long> ids = comments.stream().map(TaskComment::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                User::getId, u -> u.getNickname() == null ? u.getUsername() : u.getNickname()));
        comments.forEach(comment -> {
            comment.setAuthorName(names.getOrDefault(comment.getUserId(), "未知用户"));
            comment.setMentions(parseIds(comment.getMentionedUserIds()));
        });
    }

    private List<Long> parseIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { return null; }
    }
}
