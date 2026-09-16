package com.smartpm.service;
import com.smartpm.dto.TaskRecurrenceDTO;
import com.smartpm.entity.TaskRecurrence;
public interface TaskRecurrenceService {
    TaskRecurrence get(Long taskId);
    TaskRecurrence save(Long taskId, TaskRecurrenceDTO dto);
    void delete(Long taskId);
    void generateDueTasks();
}
