package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.entity.AiOperationLog;
import com.smartpm.service.AiOperationLogService;
import com.smartpm.service.RiskService;
import com.smartpm.vo.RiskOverviewVO;
import com.smartpm.dto.RiskActionUpdateDTO;
import com.smartpm.entity.RiskAction;
import com.smartpm.entity.RiskEvent;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project/{projectId}/risks")
@RequiredArgsConstructor
public class ProjectRiskController {
    private final RiskService riskService;
    private final AiOperationLogService aiLogService;

    @GetMapping
    public R<RiskOverviewVO> overview(@PathVariable Long projectId,
                                      @RequestParam(required = false) Long baselineId) {
        return R.ok(riskService.getOverview(projectId, baselineId));
    }

    @PutMapping("/{taskId}/action")
    public R<RiskAction> updateAction(@PathVariable Long projectId, @PathVariable Long taskId,
                                      @Valid @RequestBody RiskActionUpdateDTO dto) {
        return R.ok(riskService.updateAction(projectId, taskId, dto));
    }

    @GetMapping("/{taskId}/events")
    public R<List<RiskEvent>> events(@PathVariable Long projectId, @PathVariable Long taskId) {
        return R.ok(riskService.listEvents(projectId, taskId));
    }

    @PostMapping("/ai-analysis")
    public ResponseEntity<R<String>> aiAnalysis(@PathVariable Long projectId) {
        AiOperationLog log = aiLogService.start("RISK_ANALYSIS", projectId, null);
        try {
            String result = riskService.generateAiAnalysis(projectId);
            aiLogService.succeed(log, 0, false);
            return ResponseEntity.ok().header("X-AI-Operation-Id", String.valueOf(log.getId())).body(R.ok(result));
        } catch (RuntimeException e) {
            aiLogService.fail(log, e);
            throw e;
        }
    }
}
