package com.smartpm.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchTaskUpdateDTO {
    @NotEmpty(message = "请选择要修改的任务")
    @Size(max = 100, message = "单次最多批量修改 100 个任务")
    private List<Long> taskIds;
    private String status;
    private Long assigneeId;
    private Boolean clearAssignee;
    private String priority;
    private String dueDate;
}
