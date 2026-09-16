package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.service.AnalyticsService;
import com.smartpm.vo.AnalyticsVO;
import com.smartpm.vo.AiOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public R<AnalyticsVO> overview(@RequestParam(required = false) Long projectId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(analyticsService.getOverview(projectId, from, to));
    }

    @GetMapping("/ai-overview")
    public R<AiOverviewVO> aiOverview(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(analyticsService.getAiOverview(projectId, from, to));
    }
}
