package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data @TableName("pm_project_template_task")
public class ProjectTemplateTask {
    @TableId(type = IdType.AUTO) private Long id;
    private Long templateId;
    private Long sourceKey;
    private Long parentSourceKey;
    private String title;
    private String description;
    private String recommendedRole;
    private String priority;
    private String tags;
    private Integer relativeStartDay;
    private Integer durationDays;
    private Integer estimatedHours;
    private String acceptanceCriteria;
    private Integer orderIndex;
}
