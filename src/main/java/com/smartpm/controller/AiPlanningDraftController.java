package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.common.websocket.TaskWebSocketHandler;
import com.smartpm.dto.AiPlanningContentDTO;
import com.smartpm.dto.AiPlanningDraftVO;
import com.smartpm.dto.AiPlanningInputDTO;
import com.smartpm.entity.AiOperationLog;
import com.smartpm.entity.Task;
import com.smartpm.service.AiOperationLogService;
import com.smartpm.service.AiPlanningDraftService;
import com.smartpm.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project/{projectId}/planning")
@RequiredArgsConstructor
public class AiPlanningDraftController {
    private final AiPlanningDraftService drafts;
    private final AiOperationLogService operations;
    private final ProjectService projects;
    private final TaskWebSocketHandler websocket;

    @GetMapping("/drafts")
    public R<List<AiPlanningDraftVO>> list(@PathVariable Long projectId) {
        return R.ok(drafts.list(projectId));
    }

    @PostMapping("/drafts")
    public R<AiPlanningDraftVO> generate(@PathVariable Long projectId, @RequestBody AiPlanningInputDTO input) {
        projects.assertProjectAccess(projectId, true);
        AiOperationLog operation = operations.start("PLAN_DRAFT_" + (input == null ? "UNKNOWN" : input.getMode()),
                projectId, input == null ? null : input.getParentTaskId());
        try {
            AiPlanningDraftVO draft = drafts.generate(projectId, input, operation.getId());
            operations.succeed(operation, draft.getContent().getTasks().size(), false);
            return R.ok(draft);
        } catch (RuntimeException e) {
            operations.fail(operation, e);
            throw e;
        }
    }

    @GetMapping("/drafts/{draftId}")
    public R<AiPlanningDraftVO> get(@PathVariable Long projectId, @PathVariable Long draftId) {
        return R.ok(drafts.load(projectId, draftId));
    }

    @PutMapping("/drafts/{draftId}")
    public R<AiPlanningDraftVO> save(@PathVariable Long projectId, @PathVariable Long draftId,
                                     @RequestParam int version, @RequestBody AiPlanningContentDTO content) {
        return R.ok(drafts.save(projectId, draftId, version, content));
    }

    @PostMapping("/drafts/{draftId}/regenerate-item")
    public R<AiPlanningContentDTO.Item> regenerateItem(@PathVariable Long projectId, @PathVariable Long draftId,
                                                       @RequestParam int index) {
        return R.ok(drafts.regenerateItem(projectId, draftId, index));
    }

    @PostMapping("/drafts/{draftId}/apply")
    public R<List<Task>> apply(@PathVariable Long projectId, @PathVariable Long draftId, @RequestParam int version) {
        List<Task> created = drafts.apply(projectId, draftId, version);
        websocket.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(created);
    }
}
