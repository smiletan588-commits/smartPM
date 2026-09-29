package com.smartpm.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiPlanningDraftVO {
    private Long id;
    private Long projectId;
    private Long operationId;
    private String mode;
    private Long parentTaskId;
    private AiPlanningInputDTO input;
    private AiPlanningContentDTO content;
    private Integer version;
    private String status;
    private LocalDateTime updatedAt;
    private List<Issue> issues = new ArrayList<>();

    public record Issue(Integer taskIndex, String code, String message, boolean blocking) { }
}
