package com.smartpm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptanceChecklistDTO {
    @NotBlank @Size(max = 500)
    private String content;
    private Integer orderIndex;
}
