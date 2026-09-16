package com.smartpm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RiskOverviewVO {
    private Long baselineId;
    private String baselineName;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private List<RiskItem> risks = new ArrayList<>();
    private List<WorkloadItem> workloads = new ArrayList<>();
    private List<BlockedEdge> blockedEdges = new ArrayList<>();

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RiskItem {
        private Long taskId;
        private String title;
        private String status;
        private Long assigneeId;
        private String assigneeName;
        private int score;
        private String level;
        private List<String> factors;
        private String suggestion;
        private Boolean criticalPath;
        private Integer baselineDelayDays;
        private String actionStatus;
        private Long riskOwnerId;
        private String riskOwnerName;
        private String responsePlan;
        private LocalDate actionDueDate;
        private String actionReason;
        private LocalDateTime actionUpdatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WorkloadItem {
        private Long userId;
        private String userName;
        private int activeTasks;
        private int estimatedHours;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BlockedEdge {
        private Long taskId;
        private String taskTitle;
        private Long prerequisiteTaskId;
        private String prerequisiteTitle;
    }
}
