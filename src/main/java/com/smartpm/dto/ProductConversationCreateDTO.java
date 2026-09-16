package com.smartpm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductConversationCreateDTO {
    @NotBlank @Size(max = 255)
    private String title;
    @Pattern(regexp = "IDEA|RESEARCH|REQUIREMENT|PLANNING|REVIEW")
    private String stage;
    @Pattern(regexp = "IDEA_REFINEMENT|REQUIREMENT_CLARIFICATION|USER_STORY|PRD_DRAFT|PRIORITY|DEVILS_ADVOCATE|RELEASE_PLAN|MEETING_NOTES")
    private String mode;
}
