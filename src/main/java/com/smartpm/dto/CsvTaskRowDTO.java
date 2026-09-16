package com.smartpm.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class CsvTaskRowDTO {
    private Integer rowNumber;
    private String title;
    private String status;
    private String priority;
    private String assigneeUsername;
    private String startDate;
    private String dueDate;
    private Integer estimatedHours;
    private Integer actualHours;
    private String tags;
    private String dependencyTitles;
    private List<String> errors = new ArrayList<>();
}
