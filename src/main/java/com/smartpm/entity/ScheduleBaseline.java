package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pm_schedule_baseline")
public class ScheduleBaseline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private LocalDate projectStartDate;
    private LocalDate projectFinishDate;
    private Integer durationDays;
    private Long createdBy;
    private LocalDateTime createdAt;
}
