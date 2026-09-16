package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.*;

@Data @TableName("pm_task_recurrence")
public class TaskRecurrence {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long sourceTaskId;
    private String title;
    private String description;
    private Long assigneeId;
    private String priority;
    private String tags;
    private Integer estimatedHours;
    private String acceptanceCriteria;
    private String frequency;
    private Integer intervalValue;
    private String weekdays;
    private Integer dayOfMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dueOffsetDays;
    private LocalDate nextRunDate;
    private String lastPeriodKey;
    private Boolean active;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
