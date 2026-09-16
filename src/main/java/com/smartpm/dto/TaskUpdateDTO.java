package com.smartpm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaskUpdateDTO {

    private Long id;

    @Size(min = 1, max = 255, message = "任务标题长度应为 1-255 字")
    private String title;

    @Size(max = 5000, message = "任务描述不能超过 5000 字")
    private String description;

    private String status;

    private Long assigneeId;

    /** 项目负责人将其设为 true 时，可清空误接取的任务负责人。 */
    private Boolean clearAssignee;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "截止日期格式应为 yyyy-MM-dd")
    private String dueDate;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "开始日期格式应为 yyyy-MM-dd")
    private String startDate;

    private String priority;

    /** 标签代码，使用逗号分隔。空字符串可清空标签。 */
    private String tags;

    /** 前置任务 ID，使用逗号分隔。空字符串可清空依赖。 */
    private String dependencyIds;

    @Min(value = 0, message = "预计工时不能小于 0")
    @Max(value = 10000, message = "预计工时不能超过 10000")
    private Integer estimatedHours;

    @Min(value = 0, message = "实际工时不能小于 0")
    @Max(value = 10000, message = "实际工时不能超过 10000")
    private Integer actualHours;

    @Size(max = 5000, message = "验收标准不能超过 5000 字")
    private String acceptanceCriteria;

    private Boolean reviewRequired;

    private Integer orderIndex;
}
