package com.smartpm.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("pm_email_verification")
public class EmailVerification {
    @TableId(type=IdType.AUTO) private Long id;
    private Long userId;
    private String email;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
