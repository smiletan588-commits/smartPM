package com.smartpm.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RiskActionUpdateDTO {
    @Pattern(regexp = "OPEN|IN_PROGRESS|ACCEPTED|RESOLVED", message = "无效的风险处理状态")
    private String status;
    private Long ownerUserId;
    @Size(max = 2000, message = "应对措施不能超过 2000 字")
    private String responsePlan;
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "处理期限格式应为 yyyy-MM-dd")
    private String dueDate;
    @Size(max = 1000, message = "处理原因不能超过 1000 字")
    private String reason;
}
