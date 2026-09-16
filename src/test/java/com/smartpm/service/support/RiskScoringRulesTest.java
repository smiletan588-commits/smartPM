package com.smartpm.service.support;

import com.smartpm.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringRulesTest {
    private final LocalDate today = LocalDate.of(2026, 9, 9);
    private final LocalDateTime now = today.atTime(12, 0);

    @Test
    void addsEveryMatchedFactorAndCapsAtOneHundred() {
        Task task = task("IN_PROGRESS", today.minusDays(1), 7L, now.minusDays(8));
        RiskScoringRules.Score score = RiskScoringRules.score(task, true, 41, today, now);

        assertThat(score.value()).isEqualTo(100);
        assertThat(score.level()).isEqualTo("HIGH");
        assertThat(score.factors()).containsExactly(
                "任务已逾期", "存在未完成的前置任务",
                "进行中超过七天没有更新", "负责人待处理预计工时超过 40 小时");
    }

    @Test
    void boundaryScoresMapToExpectedLevels() {
        Task dueSoonAndUnassigned = task("TODO", today.plusDays(3), null, now);
        assertThat(RiskScoringRules.score(dueSoonAndUnassigned, false, 0, today, now).level())
                .isEqualTo("MEDIUM");

        Task blocked = task("TODO", null, 1L, now);
        assertThat(RiskScoringRules.score(blocked, true, 0, today, now).value()).isEqualTo(30);
        assertThat(RiskScoringRules.score(blocked, true, 0, today, now).level()).isEqualTo("MEDIUM");
    }

    @Test
    void healthyTaskRemainsLowAndGetsStableSuggestion() {
        Task task = task("TODO", today.plusDays(10), 1L, now);
        RiskScoringRules.Score score = RiskScoringRules.score(task, false, 8, today, now);

        assertThat(score.value()).isZero();
        assertThat(score.level()).isEqualTo("LOW");
        assertThat(score.suggestion()).isEqualTo("按计划推进并保持更新");
    }

    @Test
    void scheduleContextAddsCriticalAndBaselineDelayFactors() {
        Task task = task("TODO", today.plusDays(10), 1L, now);

        RiskScoringRules.Score shortDelay = RiskScoringRules.score(task, false, 0, today, now,
                new RiskScoringRules.ScheduleContext(true, 3));
        RiskScoringRules.Score longDelay = RiskScoringRules.score(task, false, 0, today, now,
                new RiskScoringRules.ScheduleContext(true, 4));

        assertThat(shortDelay.value()).isEqualTo(25);
        assertThat(shortDelay.factors()).contains("位于关键路径且无可用浮动", "相对计划基线延期 3 天");
        assertThat(longDelay.value()).isEqualTo(35);
        assertThat(longDelay.level()).isEqualTo("MEDIUM");
        assertThat(longDelay.suggestion()).contains("延期链路");
    }

    private Task task(String status, LocalDate dueDate, Long assigneeId, LocalDateTime updatedAt) {
        Task task = new Task();
        task.setStatus(status);
        task.setDueDate(dueDate);
        task.setAssigneeId(assigneeId);
        task.setUpdatedAt(updatedAt);
        return task;
    }
}
