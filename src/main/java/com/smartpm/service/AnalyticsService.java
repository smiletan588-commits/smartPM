package com.smartpm.service;

import com.smartpm.vo.AnalyticsVO;
import com.smartpm.vo.AiOverviewVO;
import java.time.LocalDate;

public interface AnalyticsService {

    default AnalyticsVO getOverview() { return getOverview(null, null, null); }
    AnalyticsVO getOverview(Long projectId, LocalDate from, LocalDate to);
    default AiOverviewVO getAiOverview() { return getAiOverview(null, null, null); }
    AiOverviewVO getAiOverview(Long projectId, LocalDate from, LocalDate to);
}
