package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data @TableName("pm_project_template_milestone_task")
public class ProjectTemplateMilestoneTask {
    @TableId private Long templateId;
    private Long milestoneSourceKey;
    private Long taskSourceKey;
}
