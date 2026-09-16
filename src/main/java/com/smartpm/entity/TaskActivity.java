package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_task_activity")
public class TaskActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long actorId;
    private String actionType;
    private String summary;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String actorName;
}
