package com.smartpm.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("pm_task_recurrence_run")
public class TaskRecurrenceRun {
    @TableId(type = IdType.AUTO) private Long id;
    private Long recurrenceId;
    private String periodKey;
    private Long generatedTaskId;
    private LocalDateTime createdAt;
}
