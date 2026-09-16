package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.RecycleBinService;
import com.smartpm.service.UserService;
import com.smartpm.service.CollaborationService;
import com.smartpm.vo.RecycleBinItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final WikiMapper wikiMapper;
    private final TaskAttachmentMapper attachmentMapper;
    private final AttachmentDownloadLogMapper downloadLogMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final CollaborationService collaborationService;
    private final TaskDependencyMapper dependencyMapper;
    private final MilestoneTaskMapper milestoneTaskMapper;
    private final TaskCommentMapper commentMapper;
    private final TaskActivityMapper activityMapper;
    private final NotificationMapper notificationMapper;
    private final AiOperationLogMapper aiOperationLogMapper;
    private final ScheduleBaselineMapper scheduleBaselineMapper;
    private final ScheduleBaselineItemMapper scheduleBaselineItemMapper;
    private final JdbcTemplate jdbc;

    @Value("${smartpm.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public List<RecycleBinItemVO> list() {
        Map<Long, Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()).stream()
                .collect(java.util.stream.Collectors.toMap(Project::getId, project -> project));
        Map<Long, User> users = userMapper.selectList(new LambdaQueryWrapper<User>()).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
        List<RecycleBinItemVO> items = new ArrayList<>();
        projectMapper.selectList(new LambdaQueryWrapper<Project>().isNotNull(Project::getDeletedAt))
                .forEach(project -> addIfAllowed(items, item("PROJECT", project.getId(), project.getName(), project.getId(), project.getName(), project.getDeletedAt(), project.getDeletedBy(), users), project, project.getDeletedBy()));
        taskMapper.selectList(new LambdaQueryWrapper<Task>().isNotNull(Task::getDeletedAt))
                .forEach(task -> { Project project = projects.get(task.getProjectId()); addIfAllowed(items, item("TASK", task.getId(), task.getTitle(), task.getProjectId(), project == null ? "已删除项目" : project.getName(), task.getDeletedAt(), task.getDeletedBy(), users), project, task.getDeletedBy()); });
        wikiMapper.selectList(new LambdaQueryWrapper<Wiki>().isNotNull(Wiki::getDeletedAt))
                .forEach(wiki -> { Project project = projects.get(wiki.getProjectId()); addIfAllowed(items, item("WIKI", wiki.getId(), wiki.getTitle(), wiki.getProjectId(), project == null ? "已删除项目" : project.getName(), wiki.getDeletedAt(), wiki.getDeletedBy(), users), project, wiki.getDeletedBy()); });
        attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>().isNotNull(TaskAttachment::getDeletedAt))
                .forEach(file -> { Project project = projects.get(file.getProjectId()); addIfAllowed(items, item("ATTACHMENT", file.getId(), file.getOriginalName(), file.getProjectId(), project == null ? "已删除项目" : project.getName(), file.getDeletedAt(), file.getDeletedBy(), users), project, file.getDeletedBy()); });
        items.sort(Comparator.comparing(RecycleBinItemVO::getDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    @Override
    @Transactional
    public Long restore(String type, Long id) {
        return switch (normalizeType(type)) {
            case "PROJECT" -> restoreProject(id);
            case "TASK" -> restoreTask(id);
            case "WIKI" -> restoreWiki(id);
            case "ATTACHMENT" -> restoreAttachment(id);
            default -> throw new BusinessException("不支持的回收站类型");
        };
    }

    @Override
    @Transactional
    public Long permanentlyDelete(String type, Long id) {
        if (!userService.isSystemAdmin(UserHolder.getUserId())) throw new BusinessException("仅系统管理员可以永久删除");
        return switch (normalizeType(type)) {
            case "PROJECT" -> permanentlyDeleteProject(id);
            case "TASK" -> permanentlyDeleteTask(id);
            case "WIKI" -> permanentlyDeleteWiki(id);
            case "ATTACHMENT" -> permanentlyDeleteAttachment(id);
            default -> throw new BusinessException("不支持的回收站类型");
        };
    }

    private Long restoreProject(Long id) {
        Project project = requireDeletedProject(id); requireManage(project, project.getDeletedBy());
        projectMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Project>()
                .eq(Project::getId, id).set(Project::getDeletedAt, null).set(Project::getDeletedBy, null).set(Project::getUpdatedAt, LocalDateTime.now()));
        return id;
    }

    private Long restoreTask(Long id) {
        Task task = requireDeletedTask(id); Project project = requireActiveProject(task.getProjectId()); requireManage(project, task.getDeletedBy());
        restoreTaskRow(task);
        taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getParentId, id).isNotNull(Task::getDeletedAt)).forEach(this::restoreTaskRow);
        collaborationService.record(task.getProjectId(), task.getId(), "TASK_RESTORED", "从回收站恢复了任务", null, null);
        return task.getProjectId();
    }

    private Long restoreWiki(Long id) {
        Wiki wiki = requireDeletedWiki(id); Project project = requireActiveProject(wiki.getProjectId()); requireManage(project, wiki.getDeletedBy());
        wikiMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Wiki>()
                .eq(Wiki::getId, id).set(Wiki::getDeletedAt, null).set(Wiki::getDeletedBy, null).set(Wiki::getUpdateTime, LocalDateTime.now())); return wiki.getProjectId();
    }

    private Long restoreAttachment(Long id) {
        TaskAttachment attachment = requireDeletedAttachment(id); Project project = requireActiveProject(attachment.getProjectId()); requireManage(project, attachment.getDeletedBy());
        Task task = taskMapper.selectById(attachment.getTaskId()); if (task == null || task.getDeletedAt() != null) throw new BusinessException("请先恢复所属任务");
        attachmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskAttachment>()
                .eq(TaskAttachment::getId, id).set(TaskAttachment::getDeletedAt, null).set(TaskAttachment::getDeletedBy, null)); return attachment.getProjectId();
    }

    private Long permanentlyDeleteProject(Long id) {
        Project project = requireDeletedProject(id);
        List<Long> taskIds = taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, id))
                .stream().map(Task::getId).toList();
        List<Long> milestoneIds = milestoneMapper.selectList(new LambdaQueryWrapper<ProjectMilestone>()
                .eq(ProjectMilestone::getProjectId, id)).stream().map(ProjectMilestone::getId).toList();
        List<Long> baselineIds = scheduleBaselineMapper.selectList(new LambdaQueryWrapper<ScheduleBaseline>()
                .eq(ScheduleBaseline::getProjectId, id)).stream().map(ScheduleBaseline::getId).toList();
        List<TaskAttachment> files = attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>().eq(TaskAttachment::getProjectId, id));
        deleteFilesAndRows(files);
        if (!taskIds.isEmpty()) dependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>()
                .in(TaskDependency::getTaskId, taskIds).or().in(TaskDependency::getPrerequisiteTaskId, taskIds));
        if (!milestoneIds.isEmpty()) milestoneTaskMapper.delete(new LambdaQueryWrapper<MilestoneTask>()
                .in(MilestoneTask::getMilestoneId, milestoneIds));
        if (!baselineIds.isEmpty()) scheduleBaselineItemMapper.delete(new LambdaQueryWrapper<ScheduleBaselineItem>()
                .in(ScheduleBaselineItem::getBaselineId, baselineIds));
        jdbc.update("DELETE FROM pm_risk_event WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_risk_action WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_acceptance_checklist WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_task_review WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_time_entry WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_capacity_exception WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_member_capacity WHERE project_id=?", id);
        jdbc.update("DELETE rr FROM pm_task_recurrence_run rr JOIN pm_task_recurrence r ON r.id=rr.recurrence_id WHERE r.project_id=?", id);
        jdbc.update("DELETE FROM pm_task_recurrence WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_wiki_task WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_wiki_version WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_product_message WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_product_artifact_version WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_product_decision WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_product_artifact WHERE project_id=?", id);
        jdbc.update("DELETE FROM pm_product_conversation WHERE project_id=?", id);
        jdbc.update("UPDATE pm_project_template SET source_project_id=NULL WHERE source_project_id=?", id);
        commentMapper.delete(new LambdaQueryWrapper<TaskComment>().eq(TaskComment::getProjectId, id));
        activityMapper.delete(new LambdaQueryWrapper<TaskActivity>().eq(TaskActivity::getProjectId, id));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>().eq(Notification::getProjectId, id));
        aiOperationLogMapper.delete(new LambdaQueryWrapper<AiOperationLog>().eq(AiOperationLog::getProjectId, id));
        downloadLogMapper.delete(new LambdaQueryWrapper<AttachmentDownloadLog>().eq(AttachmentDownloadLog::getProjectId, id));
        wikiMapper.delete(new LambdaQueryWrapper<Wiki>().eq(Wiki::getProjectId, id));
        milestoneMapper.delete(new LambdaQueryWrapper<ProjectMilestone>().eq(ProjectMilestone::getProjectId, id));
        scheduleBaselineMapper.delete(new LambdaQueryWrapper<ScheduleBaseline>().eq(ScheduleBaseline::getProjectId, id));
        taskMapper.delete(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, id));
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        projectMapper.deleteById(id); return project.getId();
    }

    private Long permanentlyDeleteTask(Long id) {
        Task task = requireDeletedTask(id);
        List<Long> ids = new ArrayList<>(); ids.add(id);
        taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getParentId, id)).forEach(child -> ids.add(child.getId()));
        dependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>().in(TaskDependency::getTaskId, ids)
                .or().in(TaskDependency::getPrerequisiteTaskId, ids));
        commentMapper.delete(new LambdaQueryWrapper<TaskComment>().in(TaskComment::getTaskId, ids));
        activityMapper.delete(new LambdaQueryWrapper<TaskActivity>().in(TaskActivity::getTaskId, ids));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>().in(Notification::getTaskId, ids));
        for (Long taskId : ids) {
            jdbc.update("DELETE FROM pm_risk_event WHERE task_id=?", taskId);
            jdbc.update("DELETE FROM pm_risk_action WHERE task_id=?", taskId);
            jdbc.update("DELETE FROM pm_acceptance_checklist WHERE task_id=?", taskId);
            jdbc.update("DELETE FROM pm_task_review WHERE task_id=?", taskId);
            jdbc.update("DELETE FROM pm_time_entry WHERE task_id=?", taskId);
            jdbc.update("DELETE FROM pm_wiki_task WHERE task_id=?", taskId);
            jdbc.update("DELETE rr FROM pm_task_recurrence_run rr JOIN pm_task_recurrence r ON r.id=rr.recurrence_id WHERE r.source_task_id=?", taskId);
            jdbc.update("DELETE FROM pm_task_recurrence WHERE source_task_id=?", taskId);
        }
        deleteFilesAndRows(attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>().in(TaskAttachment::getTaskId, ids)));
        downloadLogMapper.delete(new LambdaQueryWrapper<AttachmentDownloadLog>().in(AttachmentDownloadLog::getTaskId, ids));
        taskMapper.delete(new LambdaQueryWrapper<Task>().in(Task::getId, ids)); return task.getProjectId();
    }

    private Long permanentlyDeleteWiki(Long id) {
        Wiki wiki = requireDeletedWiki(id);
        jdbc.update("DELETE FROM pm_wiki_task WHERE wiki_id=?", id);
        jdbc.update("DELETE FROM pm_wiki_version WHERE wiki_id=?", id);
        jdbc.update("UPDATE pm_product_artifact SET published_wiki_id=NULL WHERE published_wiki_id=?", id);
        wikiMapper.deleteById(id); return wiki.getProjectId();
    }

    private Long permanentlyDeleteAttachment(Long id) {
        TaskAttachment attachment = requireDeletedAttachment(id); deleteFilesAndRows(List.of(attachment)); return attachment.getProjectId();
    }

    private void deleteFilesAndRows(List<TaskAttachment> files) {
        for (TaskAttachment file : files) {
            try { Files.deleteIfExists(Path.of(uploadDir, "tasks", String.valueOf(file.getTaskId()), file.getStoredName()).toAbsolutePath().normalize()); }
            catch (IOException e) { throw new BusinessException("附件文件清理失败，请重试"); }
            downloadLogMapper.delete(new LambdaQueryWrapper<AttachmentDownloadLog>().eq(AttachmentDownloadLog::getAttachmentId, file.getId()));
            attachmentMapper.deleteById(file.getId());
        }
    }

    private void restoreTaskRow(Task task) { taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
            .eq(Task::getId, task.getId()).set(Task::getDeletedAt, null).set(Task::getDeletedBy, null).set(Task::getUpdatedAt, LocalDateTime.now())); }
    private Project requireActiveProject(Long id) { Project project = projectMapper.selectById(id); if (project == null || project.getDeletedAt() != null) throw new BusinessException("请先恢复所属项目"); return project; }
    private Project requireDeletedProject(Long id) { Project project = projectMapper.selectById(id); if (project == null || project.getDeletedAt() == null) throw new BusinessException("回收站中未找到项目"); return project; }
    private Task requireDeletedTask(Long id) { Task task = taskMapper.selectById(id); if (task == null || task.getDeletedAt() == null) throw new BusinessException("回收站中未找到任务"); return task; }
    private Wiki requireDeletedWiki(Long id) { Wiki wiki = wikiMapper.selectById(id); if (wiki == null || wiki.getDeletedAt() == null) throw new BusinessException("回收站中未找到文档"); return wiki; }
    private TaskAttachment requireDeletedAttachment(Long id) { TaskAttachment item = attachmentMapper.selectById(id); if (item == null || item.getDeletedAt() == null) throw new BusinessException("回收站中未找到附件"); return item; }
    private void requireManage(Project project, Long deletedBy) { if (!canManage(project, deletedBy)) throw new BusinessException("无权恢复此数据"); }
    private void addIfAllowed(List<RecycleBinItemVO> items, RecycleBinItemVO item, Project project, Long deletedBy) { if (canManage(project, deletedBy)) items.add(item); }
    private boolean canManage(Project project, Long deletedBy) {
        Long userId = UserHolder.getUserId(); if (userService.isSystemAdmin(userId) || Objects.equals(userId, deletedBy)) return true;
        if (project == null) return false; if (Objects.equals(project.getCreatorId(), userId)) return true;
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, project.getId()).eq(ProjectMember::getUserId, userId));
        return member != null && "PROJECT_ADMIN".equals(member.getPermission());
    }
    private RecycleBinItemVO item(String type, Long id, String title, Long projectId, String projectName, LocalDateTime deletedAt, Long deletedBy, Map<Long, User> users) {
        RecycleBinItemVO item = new RecycleBinItemVO(); item.setType(type); item.setId(id); item.setTitle(title); item.setProjectId(projectId); item.setProjectName(projectName); item.setDeletedAt(deletedAt); item.setDeletedBy(deletedBy);
        User user = users.get(deletedBy); item.setDeletedByName(user == null ? "未知用户" : (user.getNickname() == null ? user.getUsername() : user.getNickname())); return item;
    }
    private String normalizeType(String type) { return type == null ? "" : type.trim().toUpperCase(Locale.ROOT); }
}
