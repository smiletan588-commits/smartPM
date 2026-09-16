package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.ScheduleBaselineCreateDTO;
import com.smartpm.dto.ScheduleSimulationDTO;
import com.smartpm.entity.*;
import com.smartpm.mapper.ScheduleBaselineItemMapper;
import com.smartpm.mapper.ScheduleBaselineMapper;
import com.smartpm.mapper.TaskDependencyMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.service.ProjectService;
import com.smartpm.service.RiskService;
import com.smartpm.service.ScheduleService;
import com.smartpm.service.support.RiskScoringRules;
import com.smartpm.service.support.ScheduleEngine;
import com.smartpm.vo.ScheduleAnalysisVO;
import com.smartpm.vo.ScheduleBaselineVO;
import com.smartpm.vo.ScheduleSimulationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {
    private final ProjectService projectService;
    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final ScheduleBaselineMapper baselineMapper;
    private final ScheduleBaselineItemMapper baselineItemMapper;
    private final ScheduleEngine scheduleEngine;
    private final RiskService riskService;

    @Override
    public ScheduleAnalysisVO analyze(Long projectId, Long baselineId) {
        projectService.assertProjectAccess(projectId, false);
        ScheduleData data = loadData(projectId);
        return toAnalysis(calculate(data, null), baselineId == null ? null : loadBaseline(projectId, baselineId));
    }

    @Override
    public ScheduleSimulationVO simulate(Long projectId, ScheduleSimulationDTO dto) {
        projectService.assertProjectAccess(projectId, false);
        if (dto.getStartDate() == null && dto.getDurationDays() == null) {
            throw new BusinessException("请至少调整模拟开始日期或工期");
        }
        ScheduleData data = loadData(projectId);
        Task target = data.rootTasks().stream().filter(task -> Objects.equals(task.getId(), dto.getTaskId()))
                .findFirst().orElseThrow(() -> new BusinessException("模拟任务不存在或不属于当前项目"));
        if ("DONE".equals(target.getStatus())) throw new BusinessException("已完成任务不能参与延期模拟");

        BaselineSnapshot baseline = dto.getBaselineId() == null ? null : loadBaseline(projectId, dto.getBaselineId());
        LocalDate simulatedStart = null;
        if (dto.getStartDate() != null) {
            try { simulatedStart = LocalDate.parse(dto.getStartDate()); }
            catch (Exception e) { throw new BusinessException("模拟开始日期格式无效"); }
        }
        ScheduleEngine.Result currentResult = calculate(data, null);
        ScheduleEngine.Result simulatedResult = calculate(data,
                new ScheduleEngine.SimulationOverride(dto.getTaskId(), simulatedStart, dto.getDurationDays()));

        ScheduleSimulationVO response = new ScheduleSimulationVO();
        response.setCurrent(toAnalysis(currentResult, baseline));
        response.setSimulated(toAnalysis(simulatedResult, baseline));
        response.setProjectFinishDeltaDays(dayDifference(currentResult.projectFinishDate(), simulatedResult.projectFinishDate()));

        Map<Long, ScoredRisk> beforeRisks = scoreSchedule(data, currentResult, baseline);
        Map<Long, ScoredRisk> afterRisks = scoreSchedule(data, simulatedResult, baseline);
        response.setRiskBefore(toRiskSummary(beforeRisks));
        response.setRiskAfter(toRiskSummary(afterRisks));

        Map<Long, ScheduleEngine.TaskResult> currentTasks = indexResults(currentResult);
        for (ScheduleEngine.TaskResult simulated : simulatedResult.tasks()) {
            ScheduleEngine.TaskResult current = currentTasks.get(simulated.taskId());
            ScoredRisk before = beforeRisks.get(simulated.taskId());
            ScoredRisk after = afterRisks.get(simulated.taskId());
            boolean changed = !Objects.equals(current.plannedStartDate(), simulated.plannedStartDate())
                    || !Objects.equals(current.plannedFinishDate(), simulated.plannedFinishDate())
                    || current.critical() != simulated.critical()
                    || !Objects.equals(scoreValue(before), scoreValue(after));
            if (!changed) continue;
            ScheduleSimulationVO.TaskChange item = new ScheduleSimulationVO.TaskChange();
            item.setTaskId(simulated.taskId());
            item.setTitle(simulated.title());
            item.setCurrentStartDate(current.plannedStartDate());
            item.setCurrentFinishDate(current.plannedFinishDate());
            item.setSimulatedStartDate(simulated.plannedStartDate());
            item.setSimulatedFinishDate(simulated.plannedFinishDate());
            item.setStartDeltaDays(dayDifference(current.plannedStartDate(), simulated.plannedStartDate()));
            item.setFinishDeltaDays(dayDifference(current.plannedFinishDate(), simulated.plannedFinishDate()));
            item.setWasCritical(current.critical());
            item.setCritical(simulated.critical());
            item.setRiskBefore(scoreValue(before));
            item.setRiskAfter(scoreValue(after));
            item.setRiskLevelBefore(before == null ? null : before.level());
            item.setRiskLevelAfter(after == null ? null : after.level());
            response.getChangedTasks().add(item);
        }
        response.getChangedTasks().sort(Comparator.comparingInt((ScheduleSimulationVO.TaskChange item) ->
                Math.abs(Optional.ofNullable(item.getFinishDeltaDays()).orElse(0))).reversed()
                .thenComparing(ScheduleSimulationVO.TaskChange::getTaskId));
        return response;
    }

    @Override
    public List<ScheduleBaselineVO> listBaselines(Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        return baselineMapper.selectList(new LambdaQueryWrapper<ScheduleBaseline>()
                        .eq(ScheduleBaseline::getProjectId, projectId)
                        .orderByDesc(ScheduleBaseline::getCreatedAt).orderByDesc(ScheduleBaseline::getId))
                .stream().map(baseline -> toBaselineVO(baseline, false)).toList();
    }

    @Override
    public ScheduleBaselineVO getBaseline(Long projectId, Long baselineId) {
        projectService.assertProjectAccess(projectId, false);
        return toBaselineVO(loadBaseline(projectId, baselineId).baseline(), true);
    }

    @Override
    @Transactional
    public ScheduleBaselineVO createBaseline(Long projectId, ScheduleBaselineCreateDTO dto) {
        projectService.assertProjectAccess(projectId, true);
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) throw new BusinessException("基线名称不能为空");
        Long duplicate = baselineMapper.selectCount(new LambdaQueryWrapper<ScheduleBaseline>()
                .eq(ScheduleBaseline::getProjectId, projectId).eq(ScheduleBaseline::getName, name));
        if (duplicate > 0) throw new BusinessException("当前项目已存在同名基线");

        ScheduleEngine.Result result = calculate(loadData(projectId), null);
        ScheduleBaseline baseline = new ScheduleBaseline();
        baseline.setProjectId(projectId);
        baseline.setName(name);
        baseline.setDescription(blankToNull(dto.getDescription()));
        baseline.setProjectStartDate(result.projectStartDate());
        baseline.setProjectFinishDate(result.projectFinishDate());
        baseline.setDurationDays(result.durationDays());
        baseline.setCreatedBy(UserHolder.getUserId());
        baseline.setCreatedAt(LocalDateTime.now());
        baselineMapper.insert(baseline);

        for (ScheduleEngine.TaskResult task : result.tasks()) {
            ScheduleBaselineItem item = new ScheduleBaselineItem();
            item.setBaselineId(baseline.getId());
            item.setTaskId(task.taskId());
            item.setTaskTitle(task.title());
            item.setPlannedStartDate(task.plannedStartDate());
            item.setPlannedFinishDate(task.plannedFinishDate());
            item.setDurationDays(task.durationDays());
            item.setPredecessorIds(serializeIds(task.predecessorIds()));
            item.setCritical(task.critical());
            item.setTotalSlackDays(task.totalSlackDays());
            item.setDateSource(task.source());
            baselineItemMapper.insert(item);
        }
        riskService.invalidate(projectId);
        return toBaselineVO(baseline, true);
    }

    @Override
    @Transactional
    public void deleteBaseline(Long projectId, Long baselineId) {
        projectService.assertProjectAccess(projectId, false);
        if (!projectService.canManageMembers(projectId)) throw new BusinessException("只有项目管理员可以删除计划基线");
        ScheduleBaseline baseline = loadBaseline(projectId, baselineId).baseline();
        baselineItemMapper.delete(new LambdaQueryWrapper<ScheduleBaselineItem>()
                .eq(ScheduleBaselineItem::getBaselineId, baseline.getId()));
        baselineMapper.deleteById(baseline.getId());
        riskService.invalidate(projectId);
    }

    private ScheduleData loadData(Long projectId) {
        List<Task> allTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId).isNull(Task::getDeletedAt));
        List<Task> rootTasks = allTasks.stream().filter(task -> task.getParentId() == null)
                .sorted(Comparator.comparing(Task::getId)).toList();
        List<Long> rootIds = rootTasks.stream().map(Task::getId).toList();
        List<TaskDependency> dependencies = rootIds.isEmpty() ? List.of() : dependencyMapper.selectList(
                new LambdaQueryWrapper<TaskDependency>().in(TaskDependency::getTaskId, rootIds));
        return new ScheduleData(allTasks, rootTasks, dependencies);
    }

    private ScheduleEngine.Result calculate(ScheduleData data, ScheduleEngine.SimulationOverride simulationOverride) {
        try { return scheduleEngine.calculate(data.rootTasks(), data.dependencies(), simulationOverride, LocalDate.now()); }
        catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
    }

    private ScheduleAnalysisVO toAnalysis(ScheduleEngine.Result result, BaselineSnapshot baseline) {
        ScheduleAnalysisVO response = new ScheduleAnalysisVO();
        response.setProjectStartDate(result.projectStartDate());
        response.setProjectFinishDate(result.projectFinishDate());
        response.setDurationDays(result.durationDays());
        response.setCriticalTaskCount((int) result.tasks().stream().filter(ScheduleEngine.TaskResult::critical).count());
        response.setInferredTaskCount((int) result.tasks().stream().filter(task -> "INFERRED".equals(task.source())).count());
        response.setCriticalPathTaskIds(result.criticalPathTaskIds());
        response.setWarnings(result.warnings());

        Map<Long, ScheduleBaselineItem> baselineItems = baseline == null ? Map.of() : baseline.items().stream()
                .collect(Collectors.toMap(ScheduleBaselineItem::getTaskId, Function.identity()));
        if (baseline != null) {
            response.setBaselineId(baseline.baseline().getId());
            response.setBaselineName(baseline.baseline().getName());
            response.setFinishVarianceDays(dayDifference(baseline.baseline().getProjectFinishDate(), result.projectFinishDate()));
        }

        Set<Long> currentIds = new HashSet<>();
        for (ScheduleEngine.TaskResult task : result.tasks()) {
            currentIds.add(task.taskId());
            ScheduleAnalysisVO.TaskItem item = toTaskItem(task);
            if (baseline == null) {
                item.setChangeType("CURRENT");
            } else {
                applyBaselineComparison(item, baselineItems.get(task.taskId()));
            }
            response.getTasks().add(item);
        }
        if (baseline != null) {
            baseline.items().stream().filter(item -> !currentIds.contains(item.getTaskId()))
                    .sorted(Comparator.comparing(ScheduleBaselineItem::getTaskId)).forEach(snapshot -> {
                        ScheduleAnalysisVO.TaskItem item = new ScheduleAnalysisVO.TaskItem();
                        item.setTaskId(snapshot.getTaskId());
                        item.setTitle(snapshot.getTaskTitle());
                        item.setStatus("REMOVED");
                        item.setBaselineStartDate(snapshot.getPlannedStartDate());
                        item.setBaselineFinishDate(snapshot.getPlannedFinishDate());
                        item.setBaselineCritical(snapshot.getCritical());
                        item.setChangeType("REMOVED");
                        response.getTasks().add(item);
                    });
        }
        return response;
    }

    private ScheduleAnalysisVO.TaskItem toTaskItem(ScheduleEngine.TaskResult task) {
        ScheduleAnalysisVO.TaskItem item = new ScheduleAnalysisVO.TaskItem();
        item.setTaskId(task.taskId());
        item.setTitle(task.title());
        item.setStatus(task.status());
        item.setDeclaredStartDate(task.declaredStartDate());
        item.setDeclaredDueDate(task.declaredDueDate());
        item.setPlannedStartDate(task.plannedStartDate());
        item.setPlannedFinishDate(task.plannedFinishDate());
        item.setLatestStartDate(task.latestStartDate());
        item.setLatestFinishDate(task.latestFinishDate());
        item.setDurationDays(task.durationDays());
        item.setTotalSlackDays(task.totalSlackDays());
        item.setCritical(task.critical());
        item.setSource(task.source());
        item.setPredecessorIds(task.predecessorIds());
        return item;
    }

    private void applyBaselineComparison(ScheduleAnalysisVO.TaskItem item, ScheduleBaselineItem snapshot) {
        if (snapshot == null) {
            item.setChangeType("ADDED");
            return;
        }
        item.setBaselineStartDate(snapshot.getPlannedStartDate());
        item.setBaselineFinishDate(snapshot.getPlannedFinishDate());
        item.setBaselineCritical(snapshot.getCritical());
        item.setStartVarianceDays(dayDifference(snapshot.getPlannedStartDate(), item.getPlannedStartDate()));
        item.setFinishVarianceDays(dayDifference(snapshot.getPlannedFinishDate(), item.getPlannedFinishDate()));
        boolean changed = !Objects.equals(item.getStartVarianceDays(), 0)
                || !Objects.equals(item.getFinishVarianceDays(), 0)
                || !Objects.equals(item.getDurationDays(), snapshot.getDurationDays())
                || !Objects.equals(item.getCritical(), snapshot.getCritical());
        item.setChangeType(changed ? "UPDATED" : "UNCHANGED");
    }

    private BaselineSnapshot loadBaseline(Long projectId, Long baselineId) {
        ScheduleBaseline baseline = baselineMapper.selectById(baselineId);
        if (baseline == null || !Objects.equals(baseline.getProjectId(), projectId)) {
            throw new BusinessException("计划基线不存在或不属于当前项目");
        }
        List<ScheduleBaselineItem> items = baselineItemMapper.selectList(new LambdaQueryWrapper<ScheduleBaselineItem>()
                .eq(ScheduleBaselineItem::getBaselineId, baselineId).orderByAsc(ScheduleBaselineItem::getTaskId));
        return new BaselineSnapshot(baseline, items);
    }

    private ScheduleBaselineVO toBaselineVO(ScheduleBaseline baseline, boolean includeItems) {
        ScheduleBaselineVO response = new ScheduleBaselineVO();
        response.setId(baseline.getId());
        response.setProjectId(baseline.getProjectId());
        response.setName(baseline.getName());
        response.setDescription(baseline.getDescription());
        response.setProjectStartDate(baseline.getProjectStartDate());
        response.setProjectFinishDate(baseline.getProjectFinishDate());
        response.setDurationDays(baseline.getDurationDays());
        response.setCreatedBy(baseline.getCreatedBy());
        response.setCreatedAt(baseline.getCreatedAt());
        List<ScheduleBaselineItem> items = baselineItemMapper.selectList(new LambdaQueryWrapper<ScheduleBaselineItem>()
                .eq(ScheduleBaselineItem::getBaselineId, baseline.getId()).orderByAsc(ScheduleBaselineItem::getTaskId));
        response.setTaskCount(items.size());
        if (includeItems) items.forEach(item -> response.getItems().add(toBaselineItem(item)));
        return response;
    }

    private ScheduleBaselineVO.Item toBaselineItem(ScheduleBaselineItem source) {
        ScheduleBaselineVO.Item item = new ScheduleBaselineVO.Item();
        item.setTaskId(source.getTaskId());
        item.setTaskTitle(source.getTaskTitle());
        item.setPlannedStartDate(source.getPlannedStartDate());
        item.setPlannedFinishDate(source.getPlannedFinishDate());
        item.setDurationDays(source.getDurationDays());
        item.setPredecessorIds(parseIds(source.getPredecessorIds()));
        item.setCritical(source.getCritical());
        item.setTotalSlackDays(source.getTotalSlackDays());
        item.setDateSource(source.getDateSource());
        return item;
    }

    private Map<Long, ScoredRisk> scoreSchedule(ScheduleData data, ScheduleEngine.Result result, BaselineSnapshot baseline) {
        Map<Long, Task> allTaskMap = data.allTasks().stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<Long, List<TaskDependency>> dependenciesByTask = data.dependencies().stream()
                .collect(Collectors.groupingBy(TaskDependency::getTaskId));
        Map<Long, Integer> workload = new HashMap<>();
        for (Task task : data.allTasks()) {
            if (task.getAssigneeId() != null && !"DONE".equals(task.getStatus())) {
                workload.merge(task.getAssigneeId(), Optional.ofNullable(task.getEstimatedHours()).orElse(0), Integer::sum);
            }
        }
        Map<Long, ScheduleBaselineItem> baselineItems = baseline == null ? Map.of() : baseline.items().stream()
                .collect(Collectors.toMap(ScheduleBaselineItem::getTaskId, Function.identity()));
        Map<Long, ScheduleEngine.TaskResult> scheduleItems = indexResults(result);
        Map<Long, ScoredRisk> scores = new HashMap<>();
        for (Task task : data.rootTasks()) {
            if ("DONE".equals(task.getStatus())) continue;
            ScheduleEngine.TaskResult schedule = scheduleItems.get(task.getId());
            boolean blocked = dependenciesByTask.getOrDefault(task.getId(), List.of()).stream()
                    .map(edge -> allTaskMap.get(edge.getPrerequisiteTaskId()))
                    .anyMatch(prerequisite -> prerequisite == null || !"DONE".equals(prerequisite.getStatus()));
            ScheduleBaselineItem snapshot = baselineItems.get(task.getId());
            int baselineDelay = snapshot == null ? 0 : Math.max(0,
                    dayDifference(snapshot.getPlannedFinishDate(), schedule.plannedFinishDate()));
            Task effectiveTask = copyForScheduleRisk(task, schedule.plannedFinishDate());
            RiskScoringRules.Score score = RiskScoringRules.score(effectiveTask, blocked,
                    workload.getOrDefault(task.getAssigneeId(), 0), LocalDate.now(), LocalDateTime.now(),
                    new RiskScoringRules.ScheduleContext(schedule.critical(), baselineDelay));
            scores.put(task.getId(), new ScoredRisk(score.value(), score.level()));
        }
        return scores;
    }

    private Task copyForScheduleRisk(Task source, LocalDate effectiveDueDate) {
        Task task = new Task();
        task.setStatus(source.getStatus());
        task.setDueDate(effectiveDueDate);
        task.setAssigneeId(source.getAssigneeId());
        task.setUpdatedAt(source.getUpdatedAt());
        return task;
    }

    private ScheduleSimulationVO.RiskSummary toRiskSummary(Map<Long, ScoredRisk> scores) {
        ScheduleSimulationVO.RiskSummary summary = new ScheduleSimulationVO.RiskSummary();
        scores.values().forEach(score -> {
            if ("HIGH".equals(score.level())) summary.setHighCount(summary.getHighCount() + 1);
            else if ("MEDIUM".equals(score.level())) summary.setMediumCount(summary.getMediumCount() + 1);
            else summary.setLowCount(summary.getLowCount() + 1);
        });
        return summary;
    }

    private Map<Long, ScheduleEngine.TaskResult> indexResults(ScheduleEngine.Result result) {
        return result.tasks().stream().collect(Collectors.toMap(ScheduleEngine.TaskResult::taskId, Function.identity()));
    }

    private Integer scoreValue(ScoredRisk risk) { return risk == null ? null : risk.score(); }

    private int dayDifference(LocalDate from, LocalDate to) {
        if (from == null || to == null) return 0;
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    private String serializeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                .map(Long::valueOf).toList(); }
        catch (NumberFormatException e) { return List.of(); }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record ScheduleData(List<Task> allTasks, List<Task> rootTasks, List<TaskDependency> dependencies) { }
    private record BaselineSnapshot(ScheduleBaseline baseline, List<ScheduleBaselineItem> items) { }
    private record ScoredRisk(int score, String level) { }
}
