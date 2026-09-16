package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.TaskRecurrenceDTO;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.ProjectService;
import com.smartpm.service.TaskRecurrenceService;
import com.smartpm.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class TaskRecurrenceServiceImpl implements TaskRecurrenceService {
    private final TaskRecurrenceMapper recurrenceMapper;
    private final TaskRecurrenceRunMapper runMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final ProjectService projectService;
    private final TaskService taskService;

    @Override
    public TaskRecurrence get(Long taskId) {
        Task task = requireTask(taskId); projectService.assertProjectAccess(task.getProjectId(), false);
        return recurrenceMapper.selectOne(new LambdaQueryWrapper<TaskRecurrence>().eq(TaskRecurrence::getSourceTaskId, taskId));
    }

    @Override
    @Transactional
    public TaskRecurrence save(Long taskId, TaskRecurrenceDTO dto) {
        Task task = requireTask(taskId); projectService.assertProjectAccess(task.getProjectId(), true);
        if (task.getParentId() != null) throw new BusinessException("仅主任务可以配置周期规则");
        LocalDate start = parse(dto.getStartDate(), "开始日期");
        LocalDate end = dto.getEndDate() == null || dto.getEndDate().isBlank() ? null : parse(dto.getEndDate(), "结束日期");
        if (end != null && end.isBefore(start)) throw new BusinessException("结束日期不能早于开始日期");
        String frequency = dto.getFrequency().toUpperCase(Locale.ROOT);
        Set<Integer> weekdays = dto.getWeekdays() == null ? Set.of() : new TreeSet<>(dto.getWeekdays());
        if ("WEEKLY".equals(frequency) && weekdays.isEmpty()) weekdays = Set.of(start.getDayOfWeek().getValue());
        if ("MONTHLY".equals(frequency) && dto.getDayOfMonth() == null) throw new BusinessException("每月重复需要指定日期");
        TaskRecurrence rule = recurrenceMapper.selectOne(new LambdaQueryWrapper<TaskRecurrence>().eq(TaskRecurrence::getSourceTaskId, taskId));
        boolean created = rule == null;
        if (created) { rule = new TaskRecurrence(); rule.setCreatedAt(LocalDateTime.now()); rule.setCreatedBy(UserHolder.getUserId()); }
        rule.setProjectId(task.getProjectId()); rule.setSourceTaskId(taskId); rule.setTitle(task.getTitle()); rule.setDescription(task.getDescription());
        rule.setAssigneeId(task.getAssigneeId()); rule.setPriority(task.getPriority()); rule.setTags(task.getTags());
        rule.setEstimatedHours(task.getEstimatedHours()); rule.setAcceptanceCriteria(task.getAcceptanceCriteria());
        rule.setFrequency(frequency); rule.setIntervalValue(dto.getIntervalValue());
        rule.setWeekdays(weekdays.stream().map(String::valueOf).collect(Collectors.joining(","))); rule.setDayOfMonth(dto.getDayOfMonth());
        rule.setStartDate(start); rule.setEndDate(end); rule.setDueOffsetDays(dto.getDueOffsetDays());
        rule.setActive(!Boolean.FALSE.equals(dto.getActive())); rule.setNextRunDate(rule.getActive() ? firstRun(rule) : null); rule.setUpdatedAt(LocalDateTime.now());
        if (created) recurrenceMapper.insert(rule); else recurrenceMapper.updateById(rule);
        return rule;
    }

    @Override
    @Transactional
    public void delete(Long taskId) {
        Task task = requireTask(taskId); projectService.assertProjectAccess(task.getProjectId(), true);
        TaskRecurrence rule = recurrenceMapper.selectOne(new LambdaQueryWrapper<TaskRecurrence>().eq(TaskRecurrence::getSourceTaskId, taskId));
        if (rule == null) return;
        runMapper.delete(new LambdaQueryWrapper<TaskRecurrenceRun>().eq(TaskRecurrenceRun::getRecurrenceId, rule.getId()));
        recurrenceMapper.deleteById(rule.getId());
    }

    @Override
    @Scheduled(cron = "0 20 * * * *")
    public void generateDueTasks() {
        LocalDate today = LocalDate.now();
        List<TaskRecurrence> due = recurrenceMapper.selectList(new LambdaQueryWrapper<TaskRecurrence>()
                .eq(TaskRecurrence::getActive, true).le(TaskRecurrence::getNextRunDate, today).orderByAsc(TaskRecurrence::getNextRunDate));
        for (TaskRecurrence rule : due) generate(rule, today);
    }

    private void generate(TaskRecurrence rule, LocalDate today) {
        int safety = 0;
        while (rule.getNextRunDate() != null && !rule.getNextRunDate().isAfter(today) && safety++ < 100) {
            LocalDate occurrence = rule.getNextRunDate();
            if (rule.getEndDate() != null && occurrence.isAfter(rule.getEndDate())) {
                rule.setActive(false); rule.setNextRunDate(null); recurrenceMapper.updateById(rule); return;
            }
            String key = occurrence.toString();
            TaskRecurrenceRun run = new TaskRecurrenceRun(); run.setRecurrenceId(rule.getId()); run.setPeriodKey(key); run.setCreatedAt(LocalDateTime.now());
            try { runMapper.insert(run); }
            catch (DuplicateKeyException duplicate) { advance(rule, occurrence); continue; }
            User actor = userMapper.selectById(rule.getCreatedBy());
            if (actor == null || !"ACTIVE".equals(actor.getStatus())) {
                runMapper.deleteById(run.getId()); log.warn("周期任务创建人不可用: recurrenceId={}", rule.getId()); return;
            }
            User previous = UserHolder.get();
            try {
                UserHolder.set(actor);
                Task created = taskService.create(rule.getProjectId(), rule.getTitle(), rule.getDescription(), rule.getAssigneeId(),
                        occurrence.plusDays(Optional.ofNullable(rule.getDueOffsetDays()).orElse(0)).toString(), occurrence.toString(),
                        rule.getPriority(), rule.getTags(), null, rule.getEstimatedHours(), null, rule.getAcceptanceCriteria());
                run.setGeneratedTaskId(created.getId()); runMapper.updateById(run); rule.setLastPeriodKey(key); advance(rule, occurrence);
            } catch (Exception e) {
                runMapper.deleteById(run.getId()); log.warn("生成周期任务失败 recurrenceId={}: {}", rule.getId(), e.getMessage()); return;
            } finally {
                if (previous == null) UserHolder.remove(); else UserHolder.set(previous);
            }
        }
    }

    private void advance(TaskRecurrence rule, LocalDate occurrence) {
        LocalDate next = nextAfter(rule, occurrence);
        if (rule.getEndDate() != null && next.isAfter(rule.getEndDate())) { rule.setActive(false); rule.setNextRunDate(null); }
        else rule.setNextRunDate(next);
        rule.setUpdatedAt(LocalDateTime.now()); recurrenceMapper.updateById(rule);
    }

    private LocalDate firstRun(TaskRecurrence rule) {
        LocalDate start = rule.getStartDate();
        if ("WEEKLY".equals(rule.getFrequency()) && !weekdays(rule).contains(start.getDayOfWeek().getValue())) return nextAfter(rule, start.minusDays(1));
        if ("MONTHLY".equals(rule.getFrequency())) return monthlyDate(start.getYear(), start.getMonthValue(), rule.getDayOfMonth(), start);
        return start;
    }

    private LocalDate nextAfter(TaskRecurrence rule, LocalDate current) {
        int interval = Math.max(1, rule.getIntervalValue());
        if ("DAILY".equals(rule.getFrequency())) return current.plusDays(interval);
        if ("MONTHLY".equals(rule.getFrequency())) {
            YearMonth month = YearMonth.from(current).plusMonths(interval);
            return month.atDay(Math.min(rule.getDayOfMonth(), month.lengthOfMonth()));
        }
        Set<Integer> weekdays = weekdays(rule);
        LocalDate startWeek = rule.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 1; i <= 3700; i++) {
            LocalDate candidate = current.plusDays(i);
            long weeks = ChronoUnit.WEEKS.between(startWeek, candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (weeks >= 0 && weeks % interval == 0 && weekdays.contains(candidate.getDayOfWeek().getValue())) return candidate;
        }
        throw new BusinessException("无法计算下一次周期任务日期");
    }

    private LocalDate monthlyDate(int year, int month, int day, LocalDate notBefore) {
        YearMonth ym = YearMonth.of(year, month); LocalDate result = ym.atDay(Math.min(day, ym.lengthOfMonth()));
        if (result.isBefore(notBefore)) { ym = ym.plusMonths(1); result = ym.atDay(Math.min(day, ym.lengthOfMonth())); }
        return result;
    }

    private Set<Integer> weekdays(TaskRecurrence rule) {
        if (rule.getWeekdays() == null || rule.getWeekdays().isBlank()) return Set.of(rule.getStartDate().getDayOfWeek().getValue());
        return Arrays.stream(rule.getWeekdays().split(",")).map(Integer::valueOf).collect(Collectors.toSet());
    }

    private LocalDate parse(String value, String label) {
        try { return LocalDate.parse(value); } catch (Exception e) { throw new BusinessException(label + "格式应为 yyyy-MM-dd"); }
    }

    private Task requireTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null) throw new BusinessException("任务不存在或已移入回收站");
        return task;
    }
}
