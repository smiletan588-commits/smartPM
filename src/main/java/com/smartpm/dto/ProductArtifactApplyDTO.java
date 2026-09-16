package com.smartpm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductArtifactApplyDTO {
    @NotBlank
    private String target;
    private Boolean confirmed = false;
    private Long targetTaskId;
    @Valid @Size(max = 50)
    private List<TaskDraft> tasks = new ArrayList<>();
    @Valid @Size(max = 50)
    private List<ChecklistDraft> checklist = new ArrayList<>();
    @Valid @Size(max = 20)
    private List<MilestoneDraft> milestones = new ArrayList<>();
    @Valid @Size(max = 30)
    private List<DecisionDraft> decisions = new ArrayList<>();

    @Data
    public static class TaskDraft {
        @NotBlank @Size(max = 255) private String title;
        @Size(max = 5000) private String description;
        private String priority;
        @Min(1) @Max(3650) private Integer durationDays;
        @Size(max = 5000) private String acceptanceCriteria;
    }

    @Data
    public static class ChecklistDraft {
        @NotBlank @Size(max = 500) private String content;
    }

    @Data
    public static class MilestoneDraft {
        @NotBlank @Size(max = 128) private String name;
        @Size(max = 2000) private String description;
        @Min(1) @Max(3650) private Integer offsetDays;
    }

    @Data
    public static class DecisionDraft {
        @NotBlank @Size(max = 255) private String title;
        @Size(max = 5000) private String decision;
        @Size(max = 5000) private String rationale;
    }
}
