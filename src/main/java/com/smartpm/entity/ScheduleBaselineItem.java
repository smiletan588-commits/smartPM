package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("pm_schedule_baseline_item")
public class ScheduleBaselineItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long taskId;
    private String taskTitle;
    private LocalDate plannedStartDate;
    private LocalDate plannedFinishDate;
    private Integer durationDays;
    private String predecessorIds;
    private Boolean critical;
    private Integer totalSlackDays;
    private String dateSource;
}
