package com.smartpm.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class NotificationPreferenceDTO {
    @Email(message="邮箱格式无效") @Size(max=255) private String email;
    private Boolean emailEnabled;
    private Boolean assignmentEnabled;
    private Boolean mentionEnabled;
    private Boolean deadlineEnabled;
    private Boolean riskEnabled;
}
