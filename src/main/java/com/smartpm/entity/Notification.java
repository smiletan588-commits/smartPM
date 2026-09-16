package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private Long taskId;
    private String type;
    private String title;
    private String content;
    private Boolean isRead;
    private String dedupeKey;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
