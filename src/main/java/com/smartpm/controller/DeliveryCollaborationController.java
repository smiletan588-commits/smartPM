package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.dto.*;
import com.smartpm.service.DeliveryCollaborationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DeliveryCollaborationController {
    private final DeliveryCollaborationService deliveryService;

    @GetMapping("/api/task/{taskId}/acceptance")
    public R<Map<String, Object>> acceptance(@PathVariable Long taskId) { return R.ok(deliveryService.acceptance(taskId)); }

    @PutMapping("/api/task/{taskId}/acceptance")
    public R<Map<String, Object>> updateAcceptance(@PathVariable Long taskId, @Valid @RequestBody AcceptanceActionDTO dto) { return R.ok(deliveryService.updateAcceptance(taskId, dto)); }

    @PostMapping("/api/task/{taskId}/acceptance/checklist")
    public R<Map<String, Object>> addChecklist(@PathVariable Long taskId, @Valid @RequestBody AcceptanceChecklistDTO dto) { return R.ok(deliveryService.addChecklist(taskId, dto)); }

    @PutMapping("/api/task/{taskId}/acceptance/checklist/{itemId}")
    public R<Map<String, Object>> toggleChecklist(@PathVariable Long taskId, @PathVariable Long itemId, @RequestParam boolean checked) { return R.ok(deliveryService.toggleChecklist(taskId, itemId, checked)); }

    @DeleteMapping("/api/task/{taskId}/acceptance/checklist/{itemId}")
    public R<Void> deleteChecklist(@PathVariable Long taskId, @PathVariable Long itemId) { deliveryService.deleteChecklist(taskId, itemId); return R.ok(); }

    @GetMapping("/api/task/{taskId}/time-entries")
    public R<List<Map<String, Object>>> timeEntries(@PathVariable Long taskId) { return R.ok(deliveryService.timeEntries(taskId)); }

    @PostMapping("/api/task/{taskId}/time-entries")
    public R<Map<String, Object>> addTimeEntry(@PathVariable Long taskId, @Valid @RequestBody TimeEntryDTO dto) { return R.ok(deliveryService.addTimeEntry(taskId, dto)); }

    @GetMapping("/api/project/{projectId}/capacity")
    public R<List<Map<String, Object>>> capacity(@PathVariable Long projectId) { return R.ok(deliveryService.capacity(projectId)); }

    @PutMapping("/api/project/{projectId}/capacity")
    public R<Map<String, Object>> updateCapacity(@PathVariable Long projectId, @Valid @RequestBody CapacityUpdateDTO dto) { return R.ok(deliveryService.updateCapacity(projectId, dto)); }

    @PostMapping("/api/project/{projectId}/capacity/exceptions")
    public R<Map<String, Object>> addCapacityException(@PathVariable Long projectId, @Valid @RequestBody CapacityExceptionDTO dto) { return R.ok(deliveryService.addCapacityException(projectId, dto)); }

    @DeleteMapping("/api/project/{projectId}/capacity/exceptions/{exceptionId}")
    public R<Void> deleteCapacityException(@PathVariable Long projectId, @PathVariable Long exceptionId) { deliveryService.deleteCapacityException(projectId, exceptionId); return R.ok(); }
}
