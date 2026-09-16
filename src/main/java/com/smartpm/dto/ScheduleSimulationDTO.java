package com.smartpm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ScheduleSimulationDTO {
    private Long baselineId;

    @NotNull(message = "请选择需要模拟的任务")
    private Long taskId;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "模拟开始日期格式应为 yyyy-MM-dd")
    private String startDate;

    @Min(value = 1, message = "模拟工期不能小于 1 天")
    @Max(value = 3650, message = "模拟工期不能超过 3650 天")
    private Integer durationDays;
}
