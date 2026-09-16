package com.smartpm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductArtifactDTO {
    private Long conversationId;
    @NotBlank
    @Pattern(regexp = "IDEA_BRIEF|PERSONA|PRD|USER_STORIES|ACCEPTANCE_CRITERIA|ROADMAP|RISK_REVIEW|DECISION_LOG")
    private String type;
    @NotBlank @Size(max = 255)
    private String title;
    @NotBlank @Size(max = 100000)
    private String content;
    @Pattern(regexp = "DRAFT|IN_REVIEW|APPROVED")
    private String status;
}
