package com.smartpm.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduleAnalysisVO {
    private Long baselineId;
    private String baselineName;
    private LocalDate projectStartDate;
    private LocalDate projectFinishDate;
    private Integer durationDays;
    private Integer criticalTaskCount;
    private Integer inferredTaskCount;
    private Integer finishVarianceDays;
    private List<Long> criticalPathTaskIds = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<TaskItem> tasks = new ArrayList<>();

    @Data
    public static class TaskItem {
        private Long taskId;
        private String title;
        private String status;
        private LocalDate declaredStartDate;
        private LocalDate declaredDueDate;
        private LocalDate plannedStartDate;
        private LocalDate plannedFinishDate;
        private LocalDate latestStartDate;
        private LocalDate latestFinishDate;
        private Integer durationDays;
        private Integer totalSlackDays;
        private Boolean critical;
        private String source;
        private List<Long> predecessorIds = new ArrayList<>();
        private LocalDate baselineStartDate;
        private LocalDate baselineFinishDate;
        private Boolean baselineCritical;
        private Integer startVarianceDays;
        private Integer finishVarianceDays;
        private String changeType;
    }
}
