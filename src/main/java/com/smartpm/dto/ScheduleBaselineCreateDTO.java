package com.smartpm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScheduleBaselineCreateDTO {
    @NotBlank(message = "基线名称不能为空")
    @Size(max = 100, message = "基线名称不能超过 100 字")
    private String name;

    @Size(max = 500, message = "基线说明不能超过 500 字")
    private String description;
}
