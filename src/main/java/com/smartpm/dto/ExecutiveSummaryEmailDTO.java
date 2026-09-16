package com.smartpm.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ExecutiveSummaryEmailDTO {
    @NotEmpty @Size(max = 50)
    private List<Long> recipientUserIds;
}
