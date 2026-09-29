package com.smartpm.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiPlanningInputDTO {
    /** INIT / PLAN / DECOMPOSE */
    private String mode;
    private Long parentTaskId;
    private String goal;
    private String deliverable;
    private String scope;
    private String projectType;
    private String deadline;
    private String team;
    private List<Long> wikiIds = new ArrayList<>();
}
