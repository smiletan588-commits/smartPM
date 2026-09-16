package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data @TableName("pm_project_template_milestone")
public class ProjectTemplateMilestone {
    @TableId(type = IdType.AUTO) private Long id;
    private Long templateId;
    private Long sourceKey;
    private String name;
    private String description;
    private Integer relativeTargetDay;
}
