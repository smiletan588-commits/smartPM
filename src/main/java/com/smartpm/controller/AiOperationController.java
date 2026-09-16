package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.dto.AiFeedbackDTO;
import com.smartpm.service.AiOperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/operations")
@RequiredArgsConstructor
public class AiOperationController {
    private final AiOperationLogService service;

    @PostMapping("/{operationId}/feedback")
    public R<Void> feedback(@PathVariable Long operationId, @Valid @RequestBody AiFeedbackDTO dto) {
        service.feedback(operationId, dto.getRating(), dto.getFeedback());
        return R.ok();
    }

    @PostMapping("/{operationId}/applied")
    public R<Void> applied(@PathVariable Long operationId,
                           @RequestParam(required = false, defaultValue = "0") Integer modifiedCount) {
        service.markApplied(operationId, modifiedCount);
        return R.ok();
    }
}
