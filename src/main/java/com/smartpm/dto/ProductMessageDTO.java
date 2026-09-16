package com.smartpm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductMessageDTO {
    @NotBlank @Size(max = 12000)
    private String message;
    private String mode;
    private Boolean includeProjectDescription = false;
    private Boolean includeRisk = false;
    private Boolean includeSchedule = false;
    @Size(max = 20) private List<Long> taskIds = new ArrayList<>();
    @Size(max = 10) private List<Long> wikiIds = new ArrayList<>();
}
