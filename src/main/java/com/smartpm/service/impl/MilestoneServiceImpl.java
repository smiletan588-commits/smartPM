package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.dto.MilestoneDTO;
import com.smartpm.entity.ProjectMilestone;
import com.smartpm.entity.MilestoneTask;
import com.smartpm.entity.Task;
import com.smartpm.mapper.ProjectMilestoneMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.MilestoneTaskMapper;
import com.smartpm.service.MilestoneService;
import com.smartpm.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final ProjectMilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final MilestoneTaskMapper milestoneTaskMapper;

    @Override
    public List<ProjectMilestone> list(Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        List<ProjectMilestone> milestones = milestoneMapper.selectList(new LambdaQueryWrapper<ProjectMilestone>()
                .eq(ProjectMilestone::getProjectId, projectId)
                .orderByAsc(ProjectMilestone::getTargetDate)
                .orderByDesc(ProjectMilestone::getCreatedAt));
        milestones.forEach(item -> item.setTaskIds(serializeTaskIds(item.getId())));
        return milestones;
    }

    @Override
    @Transactional
    public ProjectMilestone create(Long projectId, MilestoneDTO dto) {
        projectService.assertProjectAccess(projectId, true);
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setProjectId(projectId);
        String taskIds = apply(dto, milestone);
        milestone.setCreatedAt(LocalDateTime.now());
        milestone.setUpdatedAt(LocalDateTime.now());
        milestoneMapper.insert(milestone);
        replaceTasks(milestone.getId(), taskIds);
        milestone.setTaskIds(taskIds);
        return milestone;
    }

    @Override
    @Transactional
    public ProjectMilestone update(Long projectId, MilestoneDTO dto) {
        if (dto.getId() == null) throw new BusinessException("里程碑 ID 不能为空");
        projectService.assertProjectAccess(projectId, true);
        ProjectMilestone milestone = milestoneMapper.selectById(dto.getId());
        if (milestone == null || !projectId.equals(milestone.getProjectId())) {
            throw new BusinessException("里程碑不存在");
        }
        String taskIds = apply(dto, milestone);
        milestone.setUpdatedAt(LocalDateTime.now());
        milestoneMapper.updateById(milestone);
        replaceTasks(milestone.getId(), taskIds);
        milestone.setTaskIds(taskIds);
        return milestone;
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long milestoneId) {
        projectService.assertProjectAccess(projectId, true);
        ProjectMilestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null || !projectId.equals(milestone.getProjectId())) {
            throw new BusinessException("里程碑不存在");
        }
        milestoneTaskMapper.delete(new LambdaQueryWrapper<MilestoneTask>().eq(MilestoneTask::getMilestoneId, milestoneId));
        milestoneMapper.deleteById(milestoneId);
    }

    private String apply(MilestoneDTO dto, ProjectMilestone milestone) {
        if (dto.getName() == null || dto.getName().isBlank()) throw new BusinessException("里程碑名称不能为空");
        milestone.setName(dto.getName().trim());
        milestone.setDescription(dto.getDescription());
        if (dto.getTargetDate() == null || dto.getTargetDate().isBlank()) {
            milestone.setTargetDate(null);
        } else {
            try { milestone.setTargetDate(LocalDate.parse(dto.getTargetDate())); }
            catch (Exception e) { throw new BusinessException("目标日期格式无效"); }
        }
        String status = dto.getStatus() == null || dto.getStatus().isBlank()
                ? "PLANNED" : dto.getStatus().toUpperCase(Locale.ROOT);
        if (!List.of("PLANNED", "COMPLETED").contains(status)) throw new BusinessException("无效的里程碑状态");
        milestone.setStatus(status);
        milestone.setTaskIds(null);
        return normalizeTaskIds(milestone.getProjectId(), dto.getTaskIds());
    }

    private String normalizeTaskIds(Long projectId, String taskIds) {
        if (taskIds == null || taskIds.isBlank()) return null;
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        try {
            Arrays.stream(taskIds.split(",")).filter(value -> !value.isBlank())
                    .forEach(value -> ids.add(Long.valueOf(value.trim())));
        } catch (NumberFormatException e) {
            throw new BusinessException("关联任务格式无效");
        }
        if (ids.isEmpty()) return null;
        List<Task> tasks = taskMapper.selectBatchIds(ids);
        if (tasks.size() != ids.size() || tasks.stream().anyMatch(task -> !projectId.equals(task.getProjectId()))) {
            throw new BusinessException("关联任务不存在或不属于当前项目");
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void replaceTasks(Long milestoneId, String taskIds) {
        milestoneTaskMapper.delete(new LambdaQueryWrapper<MilestoneTask>().eq(MilestoneTask::getMilestoneId, milestoneId));
        if (taskIds == null || taskIds.isBlank()) return;
        Arrays.stream(taskIds.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(Long::valueOf).forEach(taskId -> milestoneTaskMapper.insert(new MilestoneTask(milestoneId, taskId)));
    }

    private String serializeTaskIds(Long milestoneId) {
        List<Long> ids = milestoneTaskMapper.selectList(new LambdaQueryWrapper<MilestoneTask>()
                        .eq(MilestoneTask::getMilestoneId, milestoneId))
                .stream().map(MilestoneTask::getTaskId).toList();
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
