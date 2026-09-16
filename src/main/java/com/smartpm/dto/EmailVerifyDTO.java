package com.smartpm.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class EmailVerifyDTO { @NotBlank private String token; }
