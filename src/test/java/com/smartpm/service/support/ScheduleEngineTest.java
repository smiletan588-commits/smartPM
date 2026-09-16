package com.smartpm.service.support;

import com.smartpm.entity.Task;
import com.smartpm.entity.TaskDependency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleEngineTest {
    private final ScheduleEngine engine = new ScheduleEngine();
    private final LocalDate today = LocalDate.of(2026, 9, 10);

    @Test
    void calculatesLinearCriticalPathAndInfersMissingDates() {
        Task first = task(1L, "需求", today, today.plusDays(1), null);
        Task second = task(2L, "开发", null, null, 16);
        Task third = task(3L, "验收", null, null, 8);

        ScheduleEngine.Result result = engine.calculate(List.of(first, second, third),
                List.of(edge(2L, 1L), edge(3L, 2L)), null, today);

        assertThat(result.projectStartDate()).isEqualTo(today);
        assertThat(result.projectFinishDate()).isEqualTo(today.plusDays(4));
        assertThat(result.durationDays()).isEqualTo(5);
        assertThat(result.criticalPathTaskIds()).containsExactly(1L, 2L, 3L);
        assertThat(result.tasks()).allMatch(ScheduleEngine.TaskResult::critical);
        assertThat(result.tasks().get(1).source()).isEqualTo("INFERRED");
        assertThat(result.tasks().get(1).plannedStartDate()).isEqualTo(today.plusDays(2));
    }

    @Test
    void diamondNetworkLeavesSlackOnShortBranch() {
        List<Task> tasks = List.of(
                task(1L, "开始", today, today, null),
                task(2L, "长分支", null, null, 16),
                task(3L, "短分支", null, null, 8),
                task(4L, "汇合", null, null, 8));
        List<TaskDependency> edges = List.of(edge(2L, 1L), edge(3L, 1L), edge(4L, 2L), edge(4L, 3L));

        ScheduleEngine.Result result = engine.calculate(tasks, edges, null, today);
        ScheduleEngine.TaskResult shortBranch = item(result, 3L);

        assertThat(result.criticalPathTaskIds()).containsExactly(1L, 2L, 4L);
        assertThat(shortBranch.critical()).isFalse();
        assertThat(shortBranch.totalSlackDays()).isEqualTo(1);
    }

    @Test
    void equalDiamondProducesMultipleCriticalBranchesAndOneStableRepresentativePath() {
        List<Task> tasks = List.of(
                task(1L, "开始", today, today, null),
                task(2L, "分支 A", null, null, 8),
                task(3L, "分支 B", null, null, 8),
                task(4L, "汇合", null, null, 8));
        List<TaskDependency> edges = List.of(edge(2L, 1L), edge(3L, 1L), edge(4L, 2L), edge(4L, 3L));

        ScheduleEngine.Result result = engine.calculate(tasks, edges, null, today);

        assertThat(result.tasks()).allMatch(ScheduleEngine.TaskResult::critical);
        assertThat(result.criticalPathTaskIds()).isIn(List.of(1L, 2L, 4L), List.of(1L, 3L, 4L));
    }

    @Test
    void independentShortNetworkKeepsItsDatesButReceivesProjectSlack() {
        List<Task> tasks = List.of(
                task(1L, "长网络开始", today, today.plusDays(1), null),
                task(2L, "长网络结束", null, null, 16),
                task(3L, "独立短任务", today, today, null));

        ScheduleEngine.Result result = engine.calculate(tasks, List.of(edge(2L, 1L)), null, today);

        assertThat(item(result, 3L).plannedStartDate()).isEqualTo(today);
        assertThat(item(result, 3L).totalSlackDays()).isEqualTo(3);
        assertThat(item(result, 3L).critical()).isFalse();
    }

    @Test
    void explicitStartActsAsConstraintAndPartialDatesUseRoundedHours() {
        Task prerequisite = task(1L, "前置", today, today, null);
        Task constrained = task(2L, "约束任务", today.plusDays(5), null, 9);
        Task dueOnly = task(3L, "只有截止日", null, today.plusDays(9), 9);

        ScheduleEngine.Result result = engine.calculate(List.of(prerequisite, constrained, dueOnly),
                List.of(edge(2L, 1L)), null, today);

        assertThat(item(result, 2L).plannedStartDate()).isEqualTo(today.plusDays(5));
        assertThat(item(result, 2L).plannedFinishDate()).isEqualTo(today.plusDays(6));
        assertThat(item(result, 2L).source()).isEqualTo("INFERRED");
        assertThat(item(result, 3L).plannedStartDate()).isEqualTo(today.plusDays(8));
        assertThat(item(result, 3L).durationDays()).isEqualTo(2);
    }

    @Test
    void simulationCascadesOnlyThroughDownstreamDates() {
        List<Task> tasks = List.of(
                task(1L, "关键任务", today, today, null),
                task(2L, "后继任务", null, null, 8),
                task(3L, "独立任务", today, today, null));
        List<TaskDependency> edges = List.of(edge(2L, 1L));
        ScheduleEngine.Result current = engine.calculate(tasks, edges, null, today);
        ScheduleEngine.Result simulated = engine.calculate(tasks, edges,
                new ScheduleEngine.SimulationOverride(1L, today.plusDays(3), 2), today);

        assertThat(item(simulated, 1L).plannedFinishDate()).isEqualTo(today.plusDays(4));
        assertThat(item(simulated, 2L).plannedStartDate()).isEqualTo(today.plusDays(5));
        assertThat(item(simulated, 3L).plannedStartDate()).isEqualTo(item(current, 3L).plannedStartDate());
    }

    @Test
    void warnsAboutOutOfScopeDependencyAndScheduleConflict() {
        Task task = task(1L, "联调", today, today, null);
        ScheduleEngine.Result result = engine.calculate(List.of(task),
                List.of(edge(1L, 99L)), new ScheduleEngine.SimulationOverride(1L, today.plusDays(2), 1), today);

        assertThat(result.warnings()).anyMatch(message -> message.contains("调度范围外"));
        assertThat(result.warnings()).anyMatch(message -> message.contains("晚于原截止日期"));
    }

    @Test
    void rejectsCycleEvenIfPersistedDataIsCorrupted() {
        List<Task> tasks = List.of(task(1L, "A", null, null, 8), task(2L, "B", null, null, 8));
        assertThatThrownBy(() -> engine.calculate(tasks, List.of(edge(1L, 2L), edge(2L, 1L)), null, today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("循环");
    }

    private ScheduleEngine.TaskResult item(ScheduleEngine.Result result, Long taskId) {
        return result.tasks().stream().filter(item -> taskId.equals(item.taskId())).findFirst().orElseThrow();
    }

    private Task task(Long id, String title, LocalDate start, LocalDate due, Integer hours) {
        Task task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setStatus("TODO");
        task.setStartDate(start);
        task.setDueDate(due);
        task.setEstimatedHours(hours);
        return task;
    }

    private TaskDependency edge(Long taskId, Long prerequisiteId) {
        return new TaskDependency(taskId, prerequisiteId);
    }
}
