package com.smartpm.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class ProjectFromTemplateDTO {
    @NotNull private Long templateId;
    @NotBlank(message="项目名称不能为空") @Size(max=255) private String name;
    @Size(max=2000) private String description;
    @NotBlank @Pattern(regexp="^\\d{4}-\\d{2}-\\d{2}$", message="项目起始日期格式应为 yyyy-MM-dd") private String startDate;
}
