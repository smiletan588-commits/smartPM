package com.smartpm.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class ProjectTemplateCreateDTO {
    @NotBlank(message="模板名称不能为空") @Size(max=100) private String name;
    @Size(max=500) private String description;
    @Pattern(regexp="PRIVATE|SYSTEM", message="无效的模板可见性") private String visibility = "PRIVATE";
}
