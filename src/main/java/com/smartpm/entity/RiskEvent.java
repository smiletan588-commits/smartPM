package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_risk_event")
public class RiskEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actionId;
    private Long projectId;
    private Long taskId;
    private Long actorId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Integer score;
    private String level;
    private String note;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String actorName;
}
