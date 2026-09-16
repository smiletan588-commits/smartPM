package com.smartpm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.time.LocalDate;

@Data
public class AnalyticsVO {

    private int totalProjects;
    private int totalTasks;
    private int completedTasks;
    private int inProgressTasks;
    private int mainTasks;
    private int subTasks;
    private int overdueTasks;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Double onTimeCompletionRate;
    private Double averageDelayDays;
    private Double averageCycleHours;
    private int completionSampleSize;
    private List<StatusItem> statusDistribution;
    private List<ProjectRankItem> projectTaskRanking;
    private List<DailyTrendItem> dailyCompletedTrend;

    @Data
    @AllArgsConstructor
    public static class StatusItem {
        private String status;
        private long count;
    }

    @Data
    @AllArgsConstructor
    public static class ProjectRankItem {
        private Long projectId;
        private String projectName;
        private long taskCount;
    }

    @Data
    @AllArgsConstructor
    public static class DailyTrendItem {
        private String date;
        private long count;
    }
}
