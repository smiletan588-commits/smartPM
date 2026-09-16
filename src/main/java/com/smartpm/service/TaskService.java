package com.smartpm.service;

import com.smartpm.dto.DragDTO;
import com.smartpm.dto.BatchTaskUpdateDTO;
import com.smartpm.dto.TaskUpdateDTO;
import com.smartpm.entity.Task;

import java.util.List;

public interface TaskService {

    Task create(Long projectId, String title, String description, Long assigneeId, String dueDate,
                String startDate, String priority, String tags, String dependencyIds,
                Integer estimatedHours, Integer actualHours, String acceptanceCriteria);

    List<Task> listByProject(Long projectId);

    Task update(TaskUpdateDTO dto);

    List<Task> batchUpdate(BatchTaskUpdateDTO dto);

    void delete(Long id);

    void drag(DragDTO dto);

    /**
     * 调用 AI 将任务拆解为 3-5 个子任务，批量插入并返回。
     */
    List<Task> decomposeTask(Long taskId);

    /**
     * 查询指定主任务下的所有子任务列表。
     */
    List<Task> listSubtasks(Long taskId);

    /**
     * 切换子任务的完成状态（TODO ↔ DONE）。
     */
    Task toggleSubtask(Long taskId);

    /**
     * AI 一键生成项目初始任务（3-5 个阶段性主任务）。
     */
    List<Task> initTasks(Long projectId);
}
