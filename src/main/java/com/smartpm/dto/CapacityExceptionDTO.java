package com.smartpm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CapacityExceptionDTO {
    @NotNull private Long userId;
    @NotNull @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String exceptionDate;
    @NotNull @DecimalMin("0.00") @DecimalMax("24.00") private BigDecimal availableHours;
    @Size(max = 500) private String reason;
}
