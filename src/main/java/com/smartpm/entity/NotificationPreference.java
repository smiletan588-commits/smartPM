package com.smartpm.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("pm_notification_preference")
public class NotificationPreference {
    @TableId private Long userId;
    private Boolean emailEnabled;
    private Boolean assignmentEnabled;
    private Boolean mentionEnabled;
    private Boolean deadlineEnabled;
    private Boolean riskEnabled;
    private LocalDateTime updatedAt;
}
