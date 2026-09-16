package com.smartpm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class WorkspaceOverviewVO {
    private int myCount;
    private int todayCount;
    private int overdueCount;
    private int weekCount;
    private int blockedCount;
    private int mentionedCount;
    private long unreadCount;
    private List<TaskItem> tasks = new ArrayList<>();
    private List<ProjectItem> projects = new ArrayList<>();

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TaskItem {
        private Long id;
        private Long projectId;
        private String projectName;
        private String title;
        private String status;
        private String priority;
        private Long assigneeId;
        private String assigneeName;
        private LocalDate startDate;
        private LocalDate dueDate;
        private boolean blocked;
        private LocalDateTime updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProjectItem {
        private Long id;
        private String name;
        private String description;
        private long myOpenTasks;
        private long overdueTasks;
        private LocalDateTime updatedAt;
    }
}
