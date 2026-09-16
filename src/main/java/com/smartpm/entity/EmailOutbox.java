package com.smartpm.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("pm_email_outbox")
public class EmailOutbox {
    @TableId(type=IdType.AUTO) private Long id;
    private Long userId;
    private String recipient;
    private String subject;
    private String body;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime sentAt;
    private String dedupeKey;
    private String lastError;
    private LocalDateTime createdAt;
}
