package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_ai_operation_log")
public class AiOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private Long taskId;
    private String scene;
    private String model;
    private String promptVersion;
    private Long durationMs;
    private Boolean success;
    private Integer generatedCount;
    private Boolean applied;
    private Integer modifiedCount;
    private Integer rating;
    private String feedback;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
