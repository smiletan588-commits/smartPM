package com.smartpm.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduleBaselineVO {
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private LocalDate projectStartDate;
    private LocalDate projectFinishDate;
    private Integer durationDays;
    private Integer taskCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long taskId;
        private String taskTitle;
        private LocalDate plannedStartDate;
        private LocalDate plannedFinishDate;
        private Integer durationDays;
        private List<Long> predecessorIds = new ArrayList<>();
        private Boolean critical;
        private Integer totalSlackDays;
        private String dateSource;
    }
}
