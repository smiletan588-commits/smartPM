package com.smartpm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TimeEntryDTO {
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private String workDate;
    @NotNull @DecimalMin("0.25") @DecimalMax("24.00")
    private BigDecimal hours;
    @Size(max = 1000) private String note;
}
