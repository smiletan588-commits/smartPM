package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.ProjectFromTemplateDTO;
import com.smartpm.dto.ProjectTemplateCreateDTO;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.ProjectService;
import com.smartpm.service.ProjectTemplateService;
import com.smartpm.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectTemplateServiceImpl implements ProjectTemplateService {
    private final ProjectTemplateMapper templateMapper;
    private final ProjectTemplateTaskMapper templateTaskMapper;
    private final ProjectTemplateDependencyMapper templateDependencyMapper;
    private final ProjectTemplateMilestoneMapper templateMilestoneMapper;
    private final ProjectTemplateMilestoneTaskMapper templateMilestoneTaskMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final MilestoneTaskMapper milestoneTaskMapper;
    private final ProjectService projectService;
    private final UserService userService;

    @Override
    public List<ProjectTemplate> list() {
        Long userId = UserHolder.getUserId();
        List<ProjectTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<ProjectTemplate>()
                .eq(ProjectTemplate::getVisibility, "SYSTEM").or().eq(ProjectTemplate::getOwnerId, userId)
                .orderByDesc(ProjectTemplate::getCreatedAt));
        for (ProjectTemplate template : templates) {
            template.setTaskCount(Math.toIntExact(templateTaskMapper.selectCount(new LambdaQueryWrapper<ProjectTemplateTask>()
                    .eq(ProjectTemplateTask::getTemplateId, template.getId()))));
            template.setMilestoneCount(Math.toIntExact(templateMilestoneMapper.selectCount(new LambdaQueryWrapper<ProjectTemplateMilestone>()
                    .eq(ProjectTemplateMilestone::getTemplateId, template.getId()))));
        }
        return templates;
    }

    @Override
    @Transactional
    public ProjectTemplate createFromProject(Long projectId, ProjectTemplateCreateDTO dto) {
        projectService.assertProjectAccess(projectId, false);
        if (!projectService.canManageMembers(projectId)) throw new BusinessException("只有项目负责人或项目管理员可以保存项目模板");
        String visibility = dto.getVisibility() == null ? "PRIVATE" : dto.getVisibility();
        if ("SYSTEM".equals(visibility) && !userService.isSystemAdmin(UserHolder.getUserId())) {
            throw new BusinessException("只有系统管理员可以发布系统模板");
        }
        ProjectTemplate template = new ProjectTemplate();
        template.setName(dto.getName().trim()); template.setDescription(dto.getDescription()); template.setVisibility(visibility);
        template.setOwnerId(UserHolder.getUserId()); template.setSourceProjectId(projectId); template.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(template);

        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
                .isNull(Task::getDeletedAt).orderByAsc(Task::getParentId).orderByAsc(Task::getOrderIndex));
        LocalDate anchor = tasks.stream().map(Task::getStartDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(LocalDate.now());
        Set<Long> sourceIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        for (Task task : tasks) {
            ProjectTemplateTask item = new ProjectTemplateTask(); item.setTemplateId(template.getId()); item.setSourceKey(task.getId());
            item.setParentSourceKey(task.getParentId()); item.setTitle(task.getTitle()); item.setDescription(task.getDescription());
            item.setRecommendedRole(task.getRecommendedRole()); item.setPriority(task.getPriority()); item.setTags(task.getTags());
            item.setRelativeStartDay(task.getStartDate() == null ? null : (int) ChronoUnit.DAYS.between(anchor, task.getStartDate()));
            item.setDurationDays(duration(task)); item.setEstimatedHours(task.getEstimatedHours());
            item.setAcceptanceCriteria(task.getAcceptanceCriteria()); item.setOrderIndex(Optional.ofNullable(task.getOrderIndex()).orElse(0));
            templateTaskMapper.insert(item);
        }
        if (!sourceIds.isEmpty()) {
            List<TaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>().in(TaskDependency::getTaskId, sourceIds));
            for (TaskDependency dep : deps) if (sourceIds.contains(dep.getPrerequisiteTaskId())) {
                ProjectTemplateDependency item = new ProjectTemplateDependency(); item.setTemplateId(template.getId());
                item.setTaskSourceKey(dep.getTaskId()); item.setPrerequisiteSourceKey(dep.getPrerequisiteTaskId()); templateDependencyMapper.insert(item);
            }
        }
        List<ProjectMilestone> milestones = milestoneMapper.selectList(new LambdaQueryWrapper<ProjectMilestone>().eq(ProjectMilestone::getProjectId, projectId));
        for (ProjectMilestone milestone : milestones) {
            ProjectTemplateMilestone item = new ProjectTemplateMilestone(); item.setTemplateId(template.getId()); item.setSourceKey(milestone.getId());
            item.setName(milestone.getName()); item.setDescription(milestone.getDescription());
            item.setRelativeTargetDay(milestone.getTargetDate() == null ? null : (int) ChronoUnit.DAYS.between(anchor, milestone.getTargetDate()));
            templateMilestoneMapper.insert(item);
            List<MilestoneTask> links = milestoneTaskMapper.selectList(new LambdaQueryWrapper<MilestoneTask>()
                    .eq(MilestoneTask::getMilestoneId, milestone.getId()));
            for (MilestoneTask link : links) if (sourceIds.contains(link.getTaskId())) {
                ProjectTemplateMilestoneTask mt = new ProjectTemplateMilestoneTask(); mt.setTemplateId(template.getId());
                mt.setMilestoneSourceKey(milestone.getId()); mt.setTaskSourceKey(link.getTaskId()); templateMilestoneTaskMapper.insert(mt);
            }
        }
        template.setTaskCount(tasks.size()); template.setMilestoneCount(milestones.size());
        return template;
    }

    @Override
    @Transactional
    public Project createProject(ProjectFromTemplateDTO dto) {
        ProjectTemplate template = requireVisible(dto.getTemplateId());
        LocalDate start;
        try { start = LocalDate.parse(dto.getStartDate()); }
        catch (Exception e) { throw new BusinessException("项目起始日期格式应为 yyyy-MM-dd"); }
        Project project = projectService.create(dto.getName().trim(), dto.getDescription() == null ? template.getDescription() : dto.getDescription());
        List<ProjectTemplateTask> items = templateTaskMapper.selectList(new LambdaQueryWrapper<ProjectTemplateTask>()
                .eq(ProjectTemplateTask::getTemplateId, template.getId()).orderByAsc(ProjectTemplateTask::getOrderIndex));
        Map<Long, ProjectTemplateTask> itemBySource = items.stream()
                .collect(Collectors.toMap(ProjectTemplateTask::getSourceKey, item -> item));
        items.sort(Comparator.comparingInt(item -> templateDepth(item, itemBySource)));
        Map<Long, Long> taskIds = new LinkedHashMap<>();
        for (ProjectTemplateTask item : items) {
            Task task = new Task(); task.setProjectId(project.getId()); task.setParentId(item.getParentSourceKey() == null ? null : taskIds.get(item.getParentSourceKey()));
            task.setTitle(item.getTitle()); task.setDescription(item.getDescription()); task.setStatus("TODO"); task.setAssigneeId(null);
            task.setRecommendedRole(item.getRecommendedRole()); task.setPriority(item.getPriority()); task.setTags(item.getTags());
            if (item.getRelativeStartDay() != null) {
                LocalDate taskStart = start.plusDays(item.getRelativeStartDay()); task.setStartDate(taskStart);
                task.setDueDate(taskStart.plusDays(Math.max(1, item.getDurationDays()) - 1L));
            }
            task.setEstimatedHours(item.getEstimatedHours()); task.setAcceptanceCriteria(item.getAcceptanceCriteria());
            task.setCreatorId(UserHolder.getUserId()); task.setOrderIndex(item.getOrderIndex()); task.setAiGenerated(false);
            task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now()); taskMapper.insert(task);
            taskIds.put(item.getSourceKey(), task.getId());
        }
        List<ProjectTemplateDependency> deps = templateDependencyMapper.selectList(new LambdaQueryWrapper<ProjectTemplateDependency>()
                .eq(ProjectTemplateDependency::getTemplateId, template.getId()));
        Map<Long, List<Long>> legacy = new HashMap<>();
        for (ProjectTemplateDependency dep : deps) {
            Long taskId = taskIds.get(dep.getTaskSourceKey()); Long predecessorId = taskIds.get(dep.getPrerequisiteSourceKey());
            if (taskId == null || predecessorId == null) continue;
            dependencyMapper.insert(new TaskDependency(taskId, predecessorId)); legacy.computeIfAbsent(taskId, ignored -> new ArrayList<>()).add(predecessorId);
        }
        legacy.forEach((taskId, predecessors) -> {
            Task task = taskMapper.selectById(taskId); task.setDependencyIds(predecessors.stream().map(String::valueOf).collect(Collectors.joining(",")));
            taskMapper.updateById(task);
        });
        List<ProjectTemplateMilestone> milestones = templateMilestoneMapper.selectList(new LambdaQueryWrapper<ProjectTemplateMilestone>()
                .eq(ProjectTemplateMilestone::getTemplateId, template.getId()));
        Map<Long, Long> milestoneIds = new HashMap<>();
        for (ProjectTemplateMilestone item : milestones) {
            ProjectMilestone milestone = new ProjectMilestone(); milestone.setProjectId(project.getId()); milestone.setName(item.getName());
            milestone.setDescription(item.getDescription()); milestone.setStatus("PLANNED");
            if (item.getRelativeTargetDay() != null) milestone.setTargetDate(start.plusDays(item.getRelativeTargetDay()));
            milestone.setCreatedAt(LocalDateTime.now()); milestone.setUpdatedAt(LocalDateTime.now()); milestoneMapper.insert(milestone);
            milestoneIds.put(item.getSourceKey(), milestone.getId());
        }
        List<ProjectTemplateMilestoneTask> milestoneLinks = templateMilestoneTaskMapper.selectList(
                new LambdaQueryWrapper<ProjectTemplateMilestoneTask>().eq(ProjectTemplateMilestoneTask::getTemplateId, template.getId()));
        for (ProjectTemplateMilestoneTask link : milestoneLinks) {
            Long milestoneId = milestoneIds.get(link.getMilestoneSourceKey()); Long taskId = taskIds.get(link.getTaskSourceKey());
            if (milestoneId != null && taskId != null) milestoneTaskMapper.insert(new MilestoneTask(milestoneId, taskId));
        }
        return project;
    }

    @Override
    @Transactional
    public void delete(Long templateId) {
        ProjectTemplate template = templateMapper.selectById(templateId);
        if (template == null) throw new BusinessException("项目模板不存在");
        if (!Objects.equals(template.getOwnerId(), UserHolder.getUserId()) && !userService.isSystemAdmin(UserHolder.getUserId())) {
            throw new BusinessException("无权删除此项目模板");
        }
        templateMilestoneTaskMapper.delete(new LambdaQueryWrapper<ProjectTemplateMilestoneTask>().eq(ProjectTemplateMilestoneTask::getTemplateId, templateId));
        templateMilestoneMapper.delete(new LambdaQueryWrapper<ProjectTemplateMilestone>().eq(ProjectTemplateMilestone::getTemplateId, templateId));
        templateDependencyMapper.delete(new LambdaQueryWrapper<ProjectTemplateDependency>().eq(ProjectTemplateDependency::getTemplateId, templateId));
        templateTaskMapper.delete(new LambdaQueryWrapper<ProjectTemplateTask>().eq(ProjectTemplateTask::getTemplateId, templateId));
        templateMapper.deleteById(templateId);
    }

    private ProjectTemplate requireVisible(Long id) {
        ProjectTemplate template = templateMapper.selectById(id);
        if (template == null || !("SYSTEM".equals(template.getVisibility()) || Objects.equals(template.getOwnerId(), UserHolder.getUserId()))) {
            throw new BusinessException("项目模板不存在或无权使用");
        }
        return template;
    }

    private int duration(Task task) {
        if (task.getStartDate() != null && task.getDueDate() != null && !task.getDueDate().isBefore(task.getStartDate())) {
            return (int) ChronoUnit.DAYS.between(task.getStartDate(), task.getDueDate()) + 1;
        }
        return task.getEstimatedHours() == null || task.getEstimatedHours() <= 0 ? 1 : Math.max(1, (int) Math.ceil(task.getEstimatedHours() / 8d));
    }

    private int templateDepth(ProjectTemplateTask item, Map<Long, ProjectTemplateTask> bySource) {
        int depth = 0;
        Long parent = item.getParentSourceKey();
        Set<Long> visited = new HashSet<>();
        while (parent != null && visited.add(parent)) {
            ProjectTemplateTask parentItem = bySource.get(parent);
            if (parentItem == null) break;
            depth++;
            parent = parentItem.getParentSourceKey();
        }
        return depth;
    }
}
