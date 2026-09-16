package com.smartpm.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class NotificationPreferenceVO {
    private String email;
    private LocalDateTime emailVerifiedAt;
    private boolean emailEnabled;
    private boolean assignmentEnabled;
    private boolean mentionEnabled;
    private boolean deadlineEnabled;
    private boolean riskEnabled;
}
