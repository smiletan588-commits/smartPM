package com.smartpm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CapacityUpdateDTO {
    @NotNull private Long userId;
    @NotNull @DecimalMin("1.00") @DecimalMax("168.00") private BigDecimal weeklyHours;
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String effectiveFrom;
}
