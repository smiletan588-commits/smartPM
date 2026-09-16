package com.smartpm.controller;
import com.smartpm.common.result.R;
import com.smartpm.dto.TaskRecurrenceDTO;
import com.smartpm.entity.TaskRecurrence;
import com.smartpm.service.TaskRecurrenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/task/{taskId}/recurrence") @RequiredArgsConstructor
public class TaskRecurrenceController {
    private final TaskRecurrenceService service;
    @GetMapping public R<TaskRecurrence> get(@PathVariable Long taskId) { return R.ok(service.get(taskId)); }
    @PutMapping public R<TaskRecurrence> save(@PathVariable Long taskId, @Valid @RequestBody TaskRecurrenceDTO dto) {
        return R.ok(service.save(taskId, dto));
    }
    @DeleteMapping public R<Void> delete(@PathVariable Long taskId) { service.delete(taskId); return R.ok(); }
}
