package com.smartpm.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptanceActionDTO {
    @Pattern(regexp = "CONFIGURE|SUBMIT|START|PASS|REJECT|RETURN_FOR_FIX|RESET")
    private String action;
    private Boolean reviewRequired;
    @Size(max = 2000) private String comment;
    private Long evidenceAttachmentId;
}
