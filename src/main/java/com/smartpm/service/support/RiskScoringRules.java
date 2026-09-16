package com.smartpm.service.support;

import com.smartpm.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 无外部依赖的确定性风险规则，便于论文复现和单元测试。 */
public final class RiskScoringRules {
    private RiskScoringRules() {}

    public static Score score(Task task, boolean blocked, int assigneePendingHours,
                              LocalDate today, LocalDateTime now) {
        return score(task, blocked, assigneePendingHours, today, now, ScheduleContext.NONE);
    }

    public static Score score(Task task, boolean blocked, int assigneePendingHours,
                              LocalDate today, LocalDateTime now, ScheduleContext schedule) {
        int score = 0;
        List<String> factors = new ArrayList<>();
        if (task.getDueDate() != null && task.getDueDate().isBefore(today)) {
            score += 40;
            factors.add("任务已逾期");
        } else if (task.getDueDate() != null && !task.getDueDate().isAfter(today.plusDays(3))) {
            score += 20;
            factors.add("三天内到期");
        }
        if (blocked) {
            score += 30;
            factors.add("存在未完成的前置任务");
        }
        if ("IN_PROGRESS".equals(task.getStatus()) && task.getUpdatedAt() != null
                && task.getUpdatedAt().isBefore(now.minusDays(7))) {
            score += 15;
            factors.add("进行中超过七天没有更新");
        }
        if (task.getAssigneeId() != null && assigneePendingHours > 40) {
            score += 15;
            factors.add("负责人待处理预计工时超过 40 小时");
        }
        if (task.getAssigneeId() == null) {
            score += 10;
            factors.add("尚未指定负责人");
        }
        if (!"DONE".equals(task.getStatus()) && schedule.critical()) {
            score += 15;
            factors.add("位于关键路径且无可用浮动");
        }
        if (!"DONE".equals(task.getStatus()) && schedule.baselineDelayDays() > 0) {
            score += schedule.baselineDelayDays() >= 4 ? 20 : 10;
            factors.add("相对计划基线延期 " + schedule.baselineDelayDays() + " 天");
        }
        int capped = Math.min(100, score);
        String level = capped >= 60 ? "HIGH" : capped >= 30 ? "MEDIUM" : "LOW";
        return new Score(capped, level, List.copyOf(factors), suggestionFor(factors));
    }

    private static String suggestionFor(List<String> factors) {
        if (factors.contains("任务已逾期")) return "立即确认阻塞原因并重新安排截止日期";
        if (factors.contains("存在未完成的前置任务")) return "优先处理前置任务或调整依赖关系";
        if (factors.stream().anyMatch(factor -> factor.startsWith("相对计划基线延期"))) return "检查延期链路并评估是否需要调整里程碑";
        if (factors.contains("位于关键路径且无可用浮动")) return "优先保障关键任务资源并持续跟踪完成时间";
        if (factors.contains("负责人待处理预计工时超过 40 小时")) return "重新平衡成员工作量";
        return factors.isEmpty() ? "按计划推进并保持更新" : "明确负责人并更新执行计划";
    }

    public record ScheduleContext(boolean critical, int baselineDelayDays) {
        public static final ScheduleContext NONE = new ScheduleContext(false, 0);
    }

    public record Score(int value, String level, List<String> factors, String suggestion) {}
}
