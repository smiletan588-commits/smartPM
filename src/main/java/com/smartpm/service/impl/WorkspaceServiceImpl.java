package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.NotificationService;
import com.smartpm.service.WorkspaceService;
import com.smartpm.vo.GlobalSearchVO;
import com.smartpm.vo.WorkspaceOverviewVO;
import com.smartpm.vo.WorkspaceTaskPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {
    private static final Set<String> SCOPES = Set.of("MY", "TODAY", "WEEK", "OVERDUE", "BLOCKED", "MENTIONED", "ALL");
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final UserMapper userMapper;
    private final WikiMapper wikiMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    @Override
    public WorkspaceOverviewVO overview() {
        Long userId = UserHolder.getUserId();
        List<Project> projects = accessibleProjects(userId);
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<Task> all = activeMainTasks(projectIds);
        Set<Long> blocked = blockedTaskIds(all);
        Set<Long> mentioned = mentionedTaskIds(userId);
        LocalDate today = LocalDate.now();

        WorkspaceOverviewVO result = new WorkspaceOverviewVO();
        List<Task> mine = all.stream().filter(t -> Objects.equals(t.getAssigneeId(), userId))
                .filter(t -> !"DONE".equals(t.getStatus())).toList();
        result.setMyCount(mine.size());
        result.setTodayCount((int) mine.stream().filter(t -> today.equals(t.getDueDate())).count());
        result.setOverdueCount((int) mine.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count());
        result.setWeekCount((int) mine.stream().filter(t -> t.getDueDate() != null && !t.getDueDate().isBefore(today)
                && !t.getDueDate().isAfter(today.plusDays(7))).count());
        result.setBlockedCount((int) mine.stream().filter(t -> blocked.contains(t.getId())).count());
        result.setMentionedCount(mentioned.size());
        result.setUnreadCount(notificationService.unreadCount());

        Map<Long, Project> projectMap = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));
        result.setTasks(mine.stream().sorted(taskUrgency(today)).limit(12)
                .map(t -> toTaskItem(t, projectMap, blocked)).toList());
        for (Project project : projects.stream().sorted(Comparator.comparing(Project::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(8).toList()) {
            long myOpen = mine.stream().filter(t -> Objects.equals(t.getProjectId(), project.getId())).count();
            long overdue = all.stream().filter(t -> Objects.equals(t.getProjectId(), project.getId()))
                    .filter(t -> !"DONE".equals(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(today)).count();
            result.getProjects().add(new WorkspaceOverviewVO.ProjectItem(project.getId(), project.getName(),
                    project.getDescription(), myOpen, overdue, project.getUpdatedAt()));
        }
        return result;
    }

    @Override
    public WorkspaceTaskPageVO tasks(String scope, String keyword, Long projectId, String status, int page, int size) {
        String normalizedScope = scope == null || scope.isBlank() ? "MY" : scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(normalizedScope)) throw new BusinessException("无效的工作台任务范围");
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw new BusinessException("无效的任务状态");
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Long userId = UserHolder.getUserId();
        List<Project> projects = accessibleProjects(userId);
        if (projectId != null && projects.stream().noneMatch(p -> Objects.equals(p.getId(), projectId))) {
            throw new BusinessException("项目不存在或无权访问");
        }
        List<Long> projectIds = projectId == null ? projects.stream().map(Project::getId).toList() : List.of(projectId);
        List<Task> all = activeMainTasks(projectIds);
        Set<Long> blocked = blockedTaskIds(all);
        Set<Long> mentioned = "MENTIONED".equals(normalizedScope) ? mentionedTaskIds(userId) : Set.of();
        LocalDate today = LocalDate.now();
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Predicate<Task> scopeFilter = scopeFilter(normalizedScope, userId, today, blocked, mentioned);
        List<Task> filtered = all.stream().filter(scopeFilter)
                .filter(t -> normalizedStatus == null || normalizedStatus.equals(t.getStatus()))
                .filter(t -> q.isEmpty() || contains(t.getTitle(), q) || contains(t.getDescription(), q) || contains(t.getTags(), q))
                .sorted(taskUrgency(today)).toList();
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        Map<Long, Project> projectMap = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));
        List<WorkspaceOverviewVO.TaskItem> items = filtered.subList(from, to).stream()
                .map(t -> toTaskItem(t, projectMap, blocked)).toList();
        return new WorkspaceTaskPageVO(items, filtered.size(), safePage, safeSize);
    }

    @Override
    public GlobalSearchVO search(String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) return new GlobalSearchVO();
        if (q.length() > 100) throw new BusinessException("搜索关键词不能超过 100 字");
        int safeLimit = Math.max(1, Math.min(20, limit));
        List<Project> projects = accessibleProjects(UserHolder.getUserId());
        List<Long> ids = projects.stream().map(Project::getId).toList();
        String lower = q.toLowerCase(Locale.ROOT);
        GlobalSearchVO result = new GlobalSearchVO();
        result.setProjects(projects.stream().filter(p -> contains(p.getName(), lower) || contains(p.getDescription(), lower))
                .limit(safeLimit).map(p -> new GlobalSearchVO.Item("PROJECT", p.getId(), p.getId(), p.getName(),
                        compact(p.getDescription()), "/project/" + p.getId())).toList());
        if (ids.isEmpty()) return result;
        List<Task> matchedTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>().in(Task::getProjectId, ids)
                .isNull(Task::getParentId).isNull(Task::getDeletedAt)
                .and(wrapper -> wrapper.like(Task::getTitle, q).or().like(Task::getDescription, q))
                .orderByDesc(Task::getUpdatedAt).last("LIMIT " + safeLimit));
        result.setTasks(matchedTasks.stream().map(t -> new GlobalSearchVO.Item("TASK", t.getId(), t.getProjectId(), t.getTitle(),
                compact(t.getDescription()), "/project/" + t.getProjectId() + "?task=" + t.getId())).toList());
        result.setWikis(wikiMapper.selectList(new LambdaQueryWrapper<Wiki>().in(Wiki::getProjectId, ids).isNull(Wiki::getDeletedAt)
                        .and(wrapper -> wrapper.like(Wiki::getTitle, q).or().like(Wiki::getContent, q))
                        .orderByDesc(Wiki::getUpdateTime).last("LIMIT " + safeLimit)).stream()
                .map(w -> new GlobalSearchVO.Item("WIKI", w.getId(), w.getProjectId(), w.getTitle(), compact(w.getContent()),
                        "/project/" + w.getProjectId() + "/wiki?wiki=" + w.getId())).toList());
        return result;
    }

    private List<Project> accessibleProjects(Long userId) {
        Set<Long> ids = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).collect(Collectors.toCollection(LinkedHashSet::new));
        projectMapper.selectList(new LambdaQueryWrapper<Project>().eq(Project::getCreatorId, userId).isNull(Project::getDeletedAt))
                .stream().map(Project::getId).forEach(ids::add);
        if (ids.isEmpty()) return List.of();
        return projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, ids).isNull(Project::getDeletedAt));
    }

    private List<Task> activeMainTasks(List<Long> projectIds) {
        if (projectIds.isEmpty()) return List.of();
        return taskMapper.selectList(new LambdaQueryWrapper<Task>().in(Task::getProjectId, projectIds)
                .isNull(Task::getParentId).isNull(Task::getDeletedAt));
    }

    private Set<Long> blockedTaskIds(List<Task> tasks) {
        if (tasks.isEmpty()) return Set.of();
        Set<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        List<TaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>().in(TaskDependency::getTaskId, taskIds));
        Set<Long> predecessorIds = deps.stream().map(TaskDependency::getPrerequisiteTaskId).collect(Collectors.toSet());
        Map<Long, Task> predecessors = predecessorIds.isEmpty() ? Map.of() : taskMapper.selectBatchIds(predecessorIds).stream()
                .collect(Collectors.toMap(Task::getId, t -> t));
        return deps.stream().filter(d -> {
            Task p = predecessors.get(d.getPrerequisiteTaskId());
            return p == null || p.getDeletedAt() != null || !"DONE".equals(p.getStatus());
        }).map(TaskDependency::getTaskId).collect(Collectors.toSet());
    }

    private Set<Long> mentionedTaskIds(Long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId)
                        .eq(Notification::getType, "COMMENT_MENTIONED").eq(Notification::getIsRead, false).isNotNull(Notification::getTaskId))
                .stream().map(Notification::getTaskId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Predicate<Task> scopeFilter(String scope, Long userId, LocalDate today, Set<Long> blocked, Set<Long> mentioned) {
        Predicate<Task> mine = t -> Objects.equals(t.getAssigneeId(), userId);
        return switch (scope) {
            case "TODAY" -> mine.and(t -> !"DONE".equals(t.getStatus()) && today.equals(t.getDueDate()));
            case "WEEK" -> mine.and(t -> !"DONE".equals(t.getStatus()) && t.getDueDate() != null
                    && !t.getDueDate().isBefore(today) && !t.getDueDate().isAfter(today.plusDays(7)));
            case "OVERDUE" -> mine.and(t -> !"DONE".equals(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(today));
            case "BLOCKED" -> mine.and(t -> !"DONE".equals(t.getStatus()) && blocked.contains(t.getId()));
            case "MENTIONED" -> t -> mentioned.contains(t.getId());
            case "ALL" -> t -> true;
            default -> mine;
        };
    }

    private WorkspaceOverviewVO.TaskItem toTaskItem(Task task, Map<Long, Project> projectMap, Set<Long> blocked) {
        User assignee = task.getAssigneeId() == null ? null : userMapper.selectById(task.getAssigneeId());
        Project project = projectMap.get(task.getProjectId());
        String assigneeName = assignee == null ? null : assignee.getNickname() == null ? assignee.getUsername() : assignee.getNickname();
        return new WorkspaceOverviewVO.TaskItem(task.getId(), task.getProjectId(), project == null ? "未知项目" : project.getName(),
                task.getTitle(), task.getStatus(), task.getPriority(), task.getAssigneeId(), assigneeName,
                task.getStartDate(), task.getDueDate(), blocked.contains(task.getId()), task.getUpdatedAt());
    }

    private Comparator<Task> taskUrgency(LocalDate today) {
        return Comparator.comparingInt((Task t) -> t.getDueDate() != null && t.getDueDate().isBefore(today) ? 0
                        : today.equals(t.getDueDate()) ? 1 : t.getDueDate() != null ? 2 : 3)
                .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean contains(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) return "";
        String compact = value.replaceAll("[#*`>\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        return compact.length() > 90 ? compact.substring(0, 90) + "…" : compact;
    }
}
