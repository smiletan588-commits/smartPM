package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.dto.ScheduleBaselineCreateDTO;
import com.smartpm.dto.ScheduleSimulationDTO;
import com.smartpm.service.ScheduleService;
import com.smartpm.vo.ScheduleAnalysisVO;
import com.smartpm.vo.ScheduleBaselineVO;
import com.smartpm.vo.ScheduleSimulationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/project/{projectId}/schedule")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService scheduleService;

    @GetMapping
    public R<ScheduleAnalysisVO> analyze(@PathVariable Long projectId,
                                         @RequestParam(required = false) Long baselineId) {
        return R.ok(scheduleService.analyze(projectId, baselineId));
    }

    @PostMapping("/simulate")
    public R<ScheduleSimulationVO> simulate(@PathVariable Long projectId,
                                             @Valid @RequestBody ScheduleSimulationDTO dto) {
        return R.ok(scheduleService.simulate(projectId, dto));
    }

    @GetMapping("/baselines")
    public R<List<ScheduleBaselineVO>> listBaselines(@PathVariable Long projectId) {
        return R.ok(scheduleService.listBaselines(projectId));
    }

    @PostMapping("/baselines")
    public R<ScheduleBaselineVO> createBaseline(@PathVariable Long projectId,
                                                 @Valid @RequestBody ScheduleBaselineCreateDTO dto) {
        return R.ok(scheduleService.createBaseline(projectId, dto));
    }

    @GetMapping("/baselines/{baselineId}")
    public R<ScheduleBaselineVO> getBaseline(@PathVariable Long projectId, @PathVariable Long baselineId) {
        return R.ok(scheduleService.getBaseline(projectId, baselineId));
    }

    @DeleteMapping("/baselines/{baselineId}")
    public R<Void> deleteBaseline(@PathVariable Long projectId, @PathVariable Long baselineId) {
        scheduleService.deleteBaseline(projectId, baselineId);
        return R.ok();
    }
}
