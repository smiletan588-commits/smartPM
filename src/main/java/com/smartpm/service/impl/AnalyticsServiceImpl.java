package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.Project;
import com.smartpm.entity.Task;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.AiOperationLog;
import com.smartpm.mapper.ProjectMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.AiOperationLogMapper;
import com.smartpm.service.AnalyticsService;
import com.smartpm.vo.AnalyticsVO;
import com.smartpm.vo.AiOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final AiOperationLogMapper aiOperationLogMapper;

    @Override
    public AnalyticsVO getOverview(Long requestedProjectId, LocalDate requestedFrom, LocalDate requestedTo) {
        Long userId = UserHolder.getUserId();
        Set<Long> projectIdSet = accessibleProjectIds(userId);
        DateRange range = dateRange(requestedFrom, requestedTo);
        if (requestedProjectId != null) {
            if (!projectIdSet.contains(requestedProjectId)) throw new com.smartpm.common.exception.BusinessException("项目不存在或无权访问");
            projectIdSet = new LinkedHashSet<>(Set.of(requestedProjectId));
        }
        List<Project> projects = projectIdSet.isEmpty() ? List.of() : projectMapper.selectList(
                new LambdaQueryWrapper<Project>().in(Project::getId, projectIdSet).isNull(Project::getDeletedAt));
        List<Long> projectIds = projects.stream().map(Project::getId).toList();

        AnalyticsVO vo = new AnalyticsVO();
        vo.setTotalProjects(projects.size());
        vo.setEffectiveFrom(range.from()); vo.setEffectiveTo(range.to());

        if (projectIds.isEmpty()) {
            vo.setTotalTasks(0);
            vo.setCompletedTasks(0);
            vo.setInProgressTasks(0);
            vo.setMainTasks(0);
            vo.setSubTasks(0);
            vo.setOverdueTasks(0);
            vo.setStatusDistribution(List.of());
            vo.setProjectTaskRanking(List.of());
            vo.setDailyCompletedTrend(buildEmptyTrend(range));
            return vo;
        }

        List<Task> allTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().in(Task::getProjectId, projectIds).isNull(Task::getDeletedAt));

        // 基础概览
        int total = allTasks.size();
        long completed = allTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long inProgress = allTasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        vo.setTotalTasks(total);
        vo.setCompletedTasks((int) completed);
        vo.setInProgressTasks((int) inProgress);
        vo.setMainTasks((int) allTasks.stream().filter(t -> t.getParentId() == null).count());
        vo.setSubTasks((int) allTasks.stream().filter(t -> t.getParentId() != null).count());
        vo.setOverdueTasks((int) allTasks.stream().filter(t -> !"DONE".equals(t.getStatus()) && t.getDueDate() != null
                && t.getDueDate().isBefore(LocalDate.now())).count());

        List<Task> periodCompleted = allTasks.stream().filter(t -> "DONE".equals(t.getStatus()) && t.getCompletedAt() != null)
                .filter(t -> !t.getCompletedAt().toLocalDate().isBefore(range.from()) && !t.getCompletedAt().toLocalDate().isAfter(range.to())).toList();
        List<Task> deadlineSample = periodCompleted.stream().filter(t -> t.getDueDate() != null).toList();
        vo.setCompletionSampleSize(deadlineSample.size());
        if (!deadlineSample.isEmpty()) {
            vo.setOnTimeCompletionRate(round(deadlineSample.stream().filter(t -> !t.getCompletedAt().toLocalDate().isAfter(t.getDueDate())).count()
                    * 100d / deadlineSample.size()));
            vo.setAverageDelayDays(round(deadlineSample.stream().mapToLong(t -> Math.max(0,
                    ChronoUnit.DAYS.between(t.getDueDate(), t.getCompletedAt().toLocalDate()))).average().orElse(0)));
        }
        if (!periodCompleted.isEmpty()) vo.setAverageCycleHours(round(periodCompleted.stream().mapToLong(t -> Math.max(0,
                ChronoUnit.HOURS.between(t.getCreatedAt(), t.getCompletedAt()))).average().orElse(0)));

        // 任务状态分布
        Map<String, Long> statusMap = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
        List<AnalyticsVO.StatusItem> statusDistribution = new ArrayList<>();
        for (String s : List.of("TODO", "IN_PROGRESS", "DONE")) {
            statusDistribution.add(new AnalyticsVO.StatusItem(s, statusMap.getOrDefault(s, 0L)));
        }
        vo.setStatusDistribution(statusDistribution);

        // 项目任务量排行
        Map<Long, Long> projectTaskCount = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getProjectId, Collectors.counting()));
        Map<Long, String> projectNames = projects.stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
        List<AnalyticsVO.ProjectRankItem> ranking = projectTaskCount.entrySet().stream()
                .map(e -> new AnalyticsVO.ProjectRankItem(e.getKey(),
                        projectNames.getOrDefault(e.getKey(), "未知项目"), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getTaskCount(), a.getTaskCount()))
                .collect(Collectors.toList());
        vo.setProjectTaskRanking(ranking);

        // 所选统计区间完成趋势
        vo.setDailyCompletedTrend(buildDailyTrend(allTasks, range));

        return vo;
    }

    private List<AnalyticsVO.DailyTrendItem> buildDailyTrend(List<Task> tasks, DateRange range) {
        List<AnalyticsVO.DailyTrendItem> trend = new ArrayList<>();
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            long count = tasks.stream()
                    .filter(t -> "DONE".equals(t.getStatus())
                            && t.getCompletedAt() != null
                            && t.getCompletedAt().toLocalDate().equals(currentDate))
                    .count();
            trend.add(new AnalyticsVO.DailyTrendItem(date.toString(), count));
        }
        return trend;
    }

    private List<AnalyticsVO.DailyTrendItem> buildEmptyTrend(DateRange range) {
        List<AnalyticsVO.DailyTrendItem> trend = new ArrayList<>();
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            trend.add(new AnalyticsVO.DailyTrendItem(date.toString(), 0));
        }
        return trend;
    }

    @Override
    public AiOverviewVO getAiOverview(Long requestedProjectId, LocalDate requestedFrom, LocalDate requestedTo) {
        Long userId = UserHolder.getUserId();
        Set<Long> projectIds = accessibleProjectIds(userId);
        if (requestedProjectId != null && !projectIds.contains(requestedProjectId)) {
            throw new com.smartpm.common.exception.BusinessException("项目不存在或无权访问");
        }
        DateRange range = dateRange(requestedFrom, requestedTo);
        LambdaQueryWrapper<AiOperationLog> logQuery = new LambdaQueryWrapper<AiOperationLog>()
                .eq(AiOperationLog::getUserId, userId).ge(AiOperationLog::getCreatedAt, range.from().atStartOfDay())
                .le(AiOperationLog::getCreatedAt, range.to().atTime(LocalTime.MAX));
        if (requestedProjectId != null) logQuery.eq(AiOperationLog::getProjectId, requestedProjectId);
        List<AiOperationLog> logs = aiOperationLogMapper.selectList(logQuery);
        AiOverviewVO result = new AiOverviewVO();
        result.setTotalCalls(logs.size());
        if (!logs.isEmpty()) {
            long successful = logs.stream().filter(log -> Boolean.TRUE.equals(log.getSuccess())).count();
            result.setSuccessRate(round(successful * 100d / logs.size()));
            result.setAverageDurationMs(Math.round(logs.stream().filter(log -> log.getDurationMs() != null)
                    .mapToLong(AiOperationLog::getDurationMs).average().orElse(0)));
            result.setAdoptionRate(successful == 0 ? 0 : round(logs.stream()
                    .filter(log -> Boolean.TRUE.equals(log.getApplied())).count() * 100d / successful));
            result.setAverageRating(round(logs.stream().filter(log -> log.getRating() != null)
                    .mapToInt(AiOperationLog::getRating).average().orElse(0)));
            result.setDurationSampleCount(logs.stream().filter(log -> log.getDurationMs() != null).count());
            result.setRatingSampleCount(logs.stream().filter(log -> log.getRating() != null).count());
            result.setAdoptionSampleCount(successful);
        }
        Set<Long> effectiveProjectIds = requestedProjectId == null ? projectIds : Set.of(requestedProjectId);
        List<Task> userTasks = effectiveProjectIds.isEmpty() ? List.of() : taskMapper.selectList(
                new LambdaQueryWrapper<Task>().in(Task::getProjectId, effectiveProjectIds).isNull(Task::getDeletedAt));
        result.setAiGeneratedTaskRate(userTasks.isEmpty() ? 0 : round(userTasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.getAiGenerated())).count() * 100d / userTasks.size()));
        return result;
    }

    private Set<Long> accessibleProjectIds(Long userId) {
        Set<Long> projectIds = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getUserId, userId)).stream()
                .map(ProjectMember::getProjectId).collect(Collectors.toCollection(LinkedHashSet::new));
        projectMapper.selectList(new LambdaQueryWrapper<Project>().eq(Project::getCreatorId, userId)
                        .isNull(Project::getDeletedAt)).stream().map(Project::getId).forEach(projectIds::add);
        if (projectIds.isEmpty()) return projectIds;
        Set<Long> activeIds = projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, projectIds)
                        .isNull(Project::getDeletedAt)).stream().map(Project::getId).collect(Collectors.toSet());
        projectIds.retainAll(activeIds);
        return projectIds;
    }

    private double round(double value) { return Math.round(value * 10d) / 10d; }

    private DateRange dateRange(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) throw new com.smartpm.common.exception.BusinessException("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(from, to) > 365) throw new com.smartpm.common.exception.BusinessException("统计日期范围不能超过 366 天");
        return new DateRange(from, to);
    }

    private record DateRange(LocalDate from, LocalDate to) {}
}
