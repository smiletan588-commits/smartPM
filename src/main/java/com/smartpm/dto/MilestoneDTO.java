package com.smartpm.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MilestoneDTO {
    private Long id;
    @Size(min = 1, max = 255, message = "里程碑名称长度应为 1-255 字")
    private String name;
    @Size(max = 2000, message = "里程碑描述不能超过 2000 字")
    private String description;
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "目标日期格式应为 yyyy-MM-dd")
    private String targetDate;
    private String status;
    /** 关联任务 ID，使用逗号分隔。 */
    private String taskIds;
}
