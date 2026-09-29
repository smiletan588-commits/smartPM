package com.smartpm.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiPlanningContentDTO {
    private String overview;
    private String noSplitReason;
    private List<String> assumptions = new ArrayList<>();
    private List<Item> tasks = new ArrayList<>();
    private List<AIProjectPlanDTO.Stage> stages = new ArrayList<>();
    private List<AIProjectPlanDTO.PlanMilestone> milestones = new ArrayList<>();
    private List<AIProjectPlanDTO.Risk> risks = new ArrayList<>();

    @Data
    public static class Item {
        private String title;
        private String description;
        private String deliverable;
        private String acceptanceCriteria;
        private String fitReason;
        private String recommendedRole;
        private String recommendedSkill;
        private String priority;
        private String tags;
        private String startDate;
        private String dueDate;
        private Integer estimatedHours;
        private Long assigneeId;
        private List<Integer> dependencyIndexes = new ArrayList<>();
    }
}
