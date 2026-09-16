package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("pm_project_template")
public class ProjectTemplate {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String description;
    private String visibility;
    private Long ownerId;
    private Long sourceProjectId;
    private LocalDateTime createdAt;
    @TableField(exist = false) private int taskCount;
    @TableField(exist = false) private int milestoneCount;
}
