package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.AIService;
import com.smartpm.service.ProjectService;
import com.smartpm.service.RiskService;
import com.smartpm.service.NotificationService;
import com.smartpm.service.support.RiskScoringRules;
import com.smartpm.service.support.ScheduleEngine;
import com.smartpm.vo.RiskOverviewVO;
import com.smartpm.dto.RiskActionUpdateDTO;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskServiceImpl implements RiskService {
    private final ProjectService projectService;
    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final ScheduleBaselineMapper baselineMapper;
    private final ScheduleBaselineItemMapper baselineItemMapper;
    private final UserMapper userMapper;
    private final AIService aiService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduleEngine scheduleEngine;
    private final RiskActionMapper riskActionMapper;
    private final RiskEventMapper riskEventMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final NotificationService notificationService;

    @Override
    public RiskOverviewVO getOverview(Long projectId, Long baselineId) {
        projectService.assertProjectAccess(projectId, false);
        ScheduleBaseline effectiveBaseline = resolveBaseline(projectId, baselineId);
        Long effectiveBaselineId = effectiveBaseline == null ? null : effectiveBaseline.getId();
        String key = riskCacheKey(projectId, effectiveBaselineId);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return objectMapper.readValue(cached, RiskOverviewVO.class);
        } catch (Exception e) { log.debug("风险缓存不可用，改为实时计算: {}", e.getMessage()); }

        RiskOverviewVO result = calculate(projectId, effectiveBaselineId);
        try { redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), 60, TimeUnit.SECONDS); }
        catch (Exception e) { log.debug("风险缓存写入失败: {}", e.getMessage()); }
        return result;
    }

    @Override
    public String generateAiAnalysis(Long projectId) {
        projectService.assertProjectAccess(projectId, true);
        String key = "smartpm:risk:ai:" + projectId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return cached;
        } catch (Exception e) { log.debug("AI 风险缓存不可用: {}", e.getMessage()); }
        RiskOverviewVO overview = getOverview(projectId, null);
        String prompt = "你是项目风险顾问。以下风险分数完全由系统规则计算，请不要修改等级，只需给出按优先级排序的可执行建议。"
                + "使用 Markdown，控制在 500 字以内。数据：" + toJson(overview);
        String result = aiService.chat(prompt);
        try { redisTemplate.opsForValue().set(key, result, 10, TimeUnit.MINUTES); }
        catch (Exception e) { log.debug("AI 风险缓存写入失败: {}", e.getMessage()); }
        return result;
    }

    @Override
    @Transactional
    public RiskAction updateAction(Long projectId, Long taskId, RiskActionUpdateDTO dto) {
        projectService.assertProjectAccess(projectId, true);
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null || !Objects.equals(projectId, task.getProjectId())) {
            throw new BusinessException("风险任务不存在或不属于当前项目");
        }
        RiskOverviewVO.RiskItem currentRisk = getOverview(projectId, null).getRisks().stream()
                .filter(item -> Objects.equals(item.getTaskId(), taskId)).findFirst().orElse(null);
        String status = dto.getStatus() == null ? "OPEN" : dto.getStatus().trim().toUpperCase(Locale.ROOT);
        RiskAction action = riskActionMapper.selectOne(new LambdaQueryWrapper<RiskAction>()
                .eq(RiskAction::getProjectId, projectId).eq(RiskAction::getTaskId, taskId));
        boolean created = action == null;
        if (created) {
            action = new RiskAction();
            action.setProjectId(projectId); action.setTaskId(taskId); action.setCreatedBy(UserHolder.getUserId());
            action.setCreatedAt(LocalDateTime.now()); action.setStatus("OPEN");
        }
        String before = action.getStatus();
        Long ownerId = dto.getOwnerUserId() != null ? dto.getOwnerUserId() : action.getOwnerUserId();
        boolean claimingUnownedRisk = action.getOwnerUserId() == null
                && Objects.equals(dto.getOwnerUserId(), UserHolder.getUserId());
        if (dto.getOwnerUserId() != null && !Objects.equals(dto.getOwnerUserId(), action.getOwnerUserId())
                && !claimingUnownedRisk && !projectService.canManageMembers(projectId)) {
            throw new BusinessException("只有项目负责人或项目管理员可以重新分派风险负责人");
        }
        if (ownerId != null) assertProjectMember(projectId, ownerId);
        String plan = dto.getResponsePlan() != null ? dto.getResponsePlan().trim() : action.getResponsePlan();
        LocalDate due = dto.getDueDate() != null ? (dto.getDueDate().isBlank() ? null : parseDate(dto.getDueDate())) : action.getDueDate();
        String reason = dto.getReason() != null ? dto.getReason().trim() : action.getReason();
        if ("IN_PROGRESS".equals(status) && (ownerId == null || plan == null || plan.isBlank() || due == null)) {
            throw new BusinessException("处理中风险必须填写负责人、应对措施和处理期限");
        }
        if ("ACCEPTED".equals(status) && (reason == null || reason.isBlank())) {
            throw new BusinessException("接受风险时必须填写原因");
        }
        if ("RESOLVED".equals(status) && !"DONE".equals(task.getStatus())
                && currentRisk != null && !"LOW".equals(currentRisk.getLevel())) {
            throw new BusinessException("当前风险仍为中高等级，请选择处理中或接受风险");
        }
        action.setOwnerUserId(ownerId); action.setStatus(status); action.setResponsePlan(plan);
        action.setDueDate(due); action.setReason(reason); action.setUpdatedBy(UserHolder.getUserId());
        action.setUpdatedAt(LocalDateTime.now());
        if (created) riskActionMapper.insert(action); else riskActionMapper.updateById(action);
        recordEvent(action, created ? "ACTION_CREATED" : "ACTION_UPDATED", before, status,
                currentRisk == null ? null : currentRisk.getScore(), currentRisk == null ? null : currentRisk.getLevel(), reason,
                UserHolder.getUserId());
        invalidate(projectId);
        return action;
    }

    @Override
    public List<RiskEvent> listEvents(Long projectId, Long taskId) {
        projectService.assertProjectAccess(projectId, false);
        Task task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(projectId, task.getProjectId())) throw new BusinessException("风险任务不存在");
        List<RiskEvent> events = riskEventMapper.selectList(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getProjectId, projectId).eq(RiskEvent::getTaskId, taskId)
                .orderByDesc(RiskEvent::getCreatedAt).last("LIMIT 100"));
        Set<Long> actorIds = events.stream().map(RiskEvent::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = actorIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(actorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? u.getUsername() : u.getNickname()));
        events.forEach(event -> event.setActorName(event.getActorId() == null ? "系统" : names.getOrDefault(event.getActorId(), "未知用户")));
        return events;
    }

    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    public void synchronizeTrackedRiskStates() {
        List<RiskAction> actions = riskActionMapper.selectList(null);
        for (RiskAction action : actions) {
            Task task = taskMapper.selectById(action.getTaskId());
            boolean done = task == null || task.getDeletedAt() != null || "DONE".equals(task.getStatus());
            String desired = done ? "RESOLVED" : action.getStatus();
            Integer score = null; String level = null;
            if (!done) {
                try {
                    RiskOverviewVO.RiskItem item = calculate(action.getProjectId(), null).getRisks().stream()
                            .filter(r -> Objects.equals(r.getTaskId(), action.getTaskId())).findFirst().orElse(null);
                    if (item != null) { score = item.getScore(); level = item.getLevel(); }
                    if (item != null && "HIGH".equals(level) && action.getOwnerUserId() != null) {
                        notificationService.create(action.getOwnerUserId(), action.getProjectId(), action.getTaskId(), "RISK_HIGH",
                                "负责的任务处于高风险", item.getTitle() + " · 风险分 " + item.getScore(),
                                "risk-high:" + action.getTaskId() + ":" + LocalDate.now());
                    }
                    if ("LOW".equals(level)) desired = "RESOLVED";
                    else if (("HIGH".equals(level) || "MEDIUM".equals(level)) && "RESOLVED".equals(action.getStatus())) desired = "OPEN";
                } catch (Exception e) { log.debug("同步风险处理状态失败: {}", e.getMessage()); continue; }
            }
            if (!Objects.equals(desired, action.getStatus())) {
                String before = action.getStatus(); action.setStatus(desired); action.setUpdatedBy(null); action.setUpdatedAt(LocalDateTime.now());
                riskActionMapper.updateById(action);
                recordEvent(action, "AUTO_STATUS_CHANGED", before, desired, score, level, "风险评分变化触发", null);
                invalidate(action.getProjectId());
            }
        }
    }

    @Override
    public void invalidate(Long projectId) {
        if (projectId == null) return;
        try {
            Set<String> keys = redisTemplate.keys("smartpm:risk:" + projectId + ":baseline:*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
            redisTemplate.delete(List.of("smartpm:risk:" + projectId, "smartpm:risk:ai:" + projectId));
        }
        catch (Exception e) { log.debug("风险缓存失效失败: {}", e.getMessage()); }
    }

    private String riskCacheKey(Long projectId, Long baselineId) {
        return "smartpm:risk:" + projectId + ":baseline:" + (baselineId == null ? "none" : baselineId);
    }

    private RiskOverviewVO calculate(Long projectId, Long requestedBaselineId) {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId).isNull(Task::getDeletedAt));
        Map<Long, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, task -> task));
        List<TaskDependency> dependencies = tasks.isEmpty() ? List.of() : dependencyMapper.selectList(
                new LambdaQueryWrapper<TaskDependency>().in(TaskDependency::getTaskId, taskMap.keySet()));
        Map<Long, List<TaskDependency>> dependencyMap = dependencies.stream().collect(Collectors.groupingBy(TaskDependency::getTaskId));
        ScheduleEngine.Result schedule;
        try { schedule = scheduleEngine.calculate(tasks, dependencies, null, LocalDate.now()); }
        catch (IllegalArgumentException e) { throw new com.smartpm.common.exception.BusinessException(e.getMessage()); }
        Map<Long, ScheduleEngine.TaskResult> scheduledTasks = schedule.tasks().stream()
                .collect(Collectors.toMap(ScheduleEngine.TaskResult::taskId, item -> item));
        ScheduleBaseline baseline = resolveBaseline(projectId, requestedBaselineId);
        Map<Long, ScheduleBaselineItem> baselineItems = baseline == null ? Map.of() : baselineItemMapper.selectList(
                        new LambdaQueryWrapper<ScheduleBaselineItem>().eq(ScheduleBaselineItem::getBaselineId, baseline.getId()))
                .stream().collect(Collectors.toMap(ScheduleBaselineItem::getTaskId, item -> item));
        Map<Long, Integer> workload = new HashMap<>();
        Map<Long, Integer> activeCount = new HashMap<>();
        for (Task task : tasks) {
            if (task.getAssigneeId() != null && !"DONE".equals(task.getStatus())) {
                workload.merge(task.getAssigneeId(), Optional.ofNullable(task.getEstimatedHours()).orElse(0), Integer::sum);
                activeCount.merge(task.getAssigneeId(), 1, Integer::sum);
            }
        }
        Set<Long> userIds = tasks.stream().map(Task::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNames = userIds.isEmpty() ? new HashMap<>() : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(
                User::getId, user -> user.getNickname() == null ? user.getUsername() : user.getNickname(), (a, b) -> a, HashMap::new));
        Map<Long, RiskAction> actions = taskMap.isEmpty() ? Map.of() : riskActionMapper.selectList(
                        new LambdaQueryWrapper<RiskAction>().eq(RiskAction::getProjectId, projectId))
                .stream().collect(Collectors.toMap(RiskAction::getTaskId, item -> item));
        Set<Long> riskOwnerIds = actions.values().stream().map(RiskAction::getOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!riskOwnerIds.isEmpty()) userMapper.selectBatchIds(riskOwnerIds).forEach(user -> userNames.put(user.getId(),
                user.getNickname() == null ? user.getUsername() : user.getNickname()));

        RiskOverviewVO result = new RiskOverviewVO();
        if (baseline != null) {
            result.setBaselineId(baseline.getId());
            result.setBaselineName(baseline.getName());
        }
        workload.forEach((userId, hours) -> result.getWorkloads().add(new RiskOverviewVO.WorkloadItem(
                userId, userNames.getOrDefault(userId, "未知成员"), activeCount.getOrDefault(userId, 0), hours)));
        result.getWorkloads().sort(Comparator.comparingInt(RiskOverviewVO.WorkloadItem::getEstimatedHours).reversed());

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (Task task : tasks) {
            if ("DONE".equals(task.getStatus())) continue;
            List<TaskDependency> refs = dependencyMap.getOrDefault(task.getId(), List.of());
            boolean blocked = false;
            for (TaskDependency ref : refs) {
                Task prerequisite = taskMap.get(ref.getPrerequisiteTaskId());
                if (prerequisite == null || !"DONE".equals(prerequisite.getStatus())) {
                    blocked = true;
                    result.getBlockedEdges().add(new RiskOverviewVO.BlockedEdge(task.getId(), task.getTitle(),
                            ref.getPrerequisiteTaskId(), prerequisite == null ? "已删除任务" : prerequisite.getTitle()));
                }
            }
            ScheduleEngine.TaskResult scheduled = scheduledTasks.get(task.getId());
            ScheduleBaselineItem baselineItem = baselineItems.get(task.getId());
            int baselineDelayDays = scheduled == null || baselineItem == null ? 0 : Math.max(0,
                    (int) java.time.temporal.ChronoUnit.DAYS.between(
                            baselineItem.getPlannedFinishDate(), scheduled.plannedFinishDate()));
            Task effectiveTask = scheduled == null ? task : copyForScheduleRisk(task, scheduled.plannedFinishDate());
            boolean critical = scheduled != null && scheduled.critical();
            RiskScoringRules.Score scored = RiskScoringRules.score(effectiveTask, blocked,
                    workload.getOrDefault(task.getAssigneeId(), 0), today, now,
                    new RiskScoringRules.ScheduleContext(critical, baselineDelayDays));
            int score = scored.value();
            String level = scored.level();
            RiskOverviewVO.RiskItem risk = new RiskOverviewVO.RiskItem();
            risk.setTaskId(task.getId()); risk.setTitle(task.getTitle()); risk.setStatus(task.getStatus());
            risk.setAssigneeId(task.getAssigneeId()); risk.setAssigneeName(task.getAssigneeId() == null ? null : userNames.get(task.getAssigneeId()));
            risk.setScore(score); risk.setLevel(level); risk.setFactors(scored.factors()); risk.setSuggestion(scored.suggestion());
            risk.setCriticalPath(critical); risk.setBaselineDelayDays(baselineDelayDays);
            RiskAction action = actions.get(task.getId());
            risk.setActionStatus(action == null ? "OPEN" : action.getStatus());
            if (action != null) {
                risk.setRiskOwnerId(action.getOwnerUserId()); risk.setRiskOwnerName(userNames.get(action.getOwnerUserId()));
                risk.setResponsePlan(action.getResponsePlan()); risk.setActionDueDate(action.getDueDate());
                risk.setActionReason(action.getReason()); risk.setActionUpdatedAt(action.getUpdatedAt());
            }
            result.getRisks().add(risk);
            if ("HIGH".equals(level)) result.setHighCount(result.getHighCount() + 1);
            else if ("MEDIUM".equals(level)) result.setMediumCount(result.getMediumCount() + 1);
            else result.setLowCount(result.getLowCount() + 1);
        }
        result.getRisks().sort(Comparator.comparingInt(RiskOverviewVO.RiskItem::getScore).reversed());
        return result;
    }

    private ScheduleBaseline resolveBaseline(Long projectId, Long baselineId) {
        if (baselineId != null) {
            ScheduleBaseline baseline = baselineMapper.selectById(baselineId);
            if (baseline == null || !Objects.equals(projectId, baseline.getProjectId())) {
                throw new com.smartpm.common.exception.BusinessException("计划基线不存在或不属于当前项目");
            }
            return baseline;
        }
        return baselineMapper.selectOne(new LambdaQueryWrapper<ScheduleBaseline>()
                .eq(ScheduleBaseline::getProjectId, projectId)
                .orderByDesc(ScheduleBaseline::getCreatedAt).orderByDesc(ScheduleBaseline::getId)
                .last("LIMIT 1"));
    }

    private Task copyForScheduleRisk(Task source, LocalDate effectiveDueDate) {
        Task task = new Task();
        task.setStatus(source.getStatus());
        task.setDueDate(effectiveDueDate);
        task.setAssigneeId(source.getAssigneeId());
        task.setUpdatedAt(source.getUpdatedAt());
        return task;
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    private void assertProjectMember(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project != null && Objects.equals(project.getCreatorId(), userId)) return;
        if (memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)) == 0) throw new BusinessException("风险负责人不是当前项目成员");
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (Exception e) { throw new BusinessException("处理期限格式应为 yyyy-MM-dd"); }
    }

    private void recordEvent(RiskAction action, String type, String from, String to, Integer score,
                             String level, String note, Long actorId) {
        RiskEvent event = new RiskEvent();
        event.setActionId(action.getId()); event.setProjectId(action.getProjectId()); event.setTaskId(action.getTaskId());
        event.setActorId(actorId); event.setEventType(type); event.setFromStatus(from); event.setToStatus(to);
        event.setScore(score); event.setLevel(level); event.setNote(note); event.setCreatedAt(LocalDateTime.now());
        riskEventMapper.insert(event);
    }
}
