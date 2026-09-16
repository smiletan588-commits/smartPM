package com.smartpm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class WorkspaceTaskPageVO {
    private List<WorkspaceOverviewVO.TaskItem> items;
    private long total;
    private int page;
    private int size;
}
