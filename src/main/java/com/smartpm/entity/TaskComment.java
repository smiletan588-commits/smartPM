package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("pm_task_comment")
public class TaskComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long projectId;
    private Long userId;
    private String content;
    private String mentionedUserIds;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    @TableField(exist = false)
    private String authorName;
    @TableField(exist = false)
    private List<Long> mentions;
}
