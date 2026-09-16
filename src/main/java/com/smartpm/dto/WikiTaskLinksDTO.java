package com.smartpm.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WikiTaskLinksDTO {
    @Size(max = 100, message = "单篇文档最多关联 100 个任务")
    private List<Long> taskIds;
}
