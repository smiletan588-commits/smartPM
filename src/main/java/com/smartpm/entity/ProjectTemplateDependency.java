package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data @TableName("pm_project_template_dependency")
public class ProjectTemplateDependency {
    @TableId private Long templateId;
    private Long taskSourceKey;
    private Long prerequisiteSourceKey;
}
