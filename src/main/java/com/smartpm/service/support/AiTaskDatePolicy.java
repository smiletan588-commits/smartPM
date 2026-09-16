package com.smartpm.service.support;

import com.smartpm.entity.Task;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * AI 任务日期的统一兜底规则。
 *
 * <p>AI 返回的合法日期会被保留；缺少开始日期时从生成当天开始，缺少或无效的
 * 截止日期则设置为开始日期后的 15 天。</p>
 */
public final class AiTaskDatePolicy {

    public static final int DEFAULT_DURATION_DAYS = 15;
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private AiTaskDatePolicy() {
    }

    public static void apply(Task task) {
        DateRange range = resolve(task.getStartDate(), task.getDueDate());
        task.setStartDate(range.startDate());
        task.setDueDate(range.dueDate());
    }

    public static DateRange resolve(LocalDate startDate, LocalDate dueDate) {
        return resolve(startDate, dueDate, LocalDate.now(APP_ZONE));
    }

    public static DateRange resolve(LocalDate startDate, LocalDate dueDate, LocalDate generatedOn) {
        LocalDate resolvedStart = startDate == null ? generatedOn : startDate;
        LocalDate resolvedDue = dueDate == null || dueDate.isBefore(resolvedStart)
                ? resolvedStart.plusDays(DEFAULT_DURATION_DAYS)
                : dueDate;
        return new DateRange(resolvedStart, resolvedDue);
    }

    public record DateRange(LocalDate startDate, LocalDate dueDate) {
    }
}
