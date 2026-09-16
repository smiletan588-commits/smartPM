package com.smartpm.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduleSimulationVO {
    private ScheduleAnalysisVO current;
    private ScheduleAnalysisVO simulated;
    private Integer projectFinishDeltaDays;
    private RiskSummary riskBefore;
    private RiskSummary riskAfter;
    private List<TaskChange> changedTasks = new ArrayList<>();

    @Data
    public static class RiskSummary {
        private Integer highCount = 0;
        private Integer mediumCount = 0;
        private Integer lowCount = 0;
    }

    @Data
    public static class TaskChange {
        private Long taskId;
        private String title;
        private LocalDate currentStartDate;
        private LocalDate currentFinishDate;
        private LocalDate simulatedStartDate;
        private LocalDate simulatedFinishDate;
        private Integer startDeltaDays;
        private Integer finishDeltaDays;
        private Boolean wasCritical;
        private Boolean critical;
        private Integer riskBefore;
        private Integer riskAfter;
        private String riskLevelBefore;
        private String riskLevelAfter;
    }
}
