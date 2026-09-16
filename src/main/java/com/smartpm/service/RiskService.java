package com.smartpm.service;

import com.smartpm.vo.RiskOverviewVO;
import com.smartpm.dto.RiskActionUpdateDTO;
import com.smartpm.entity.RiskAction;
import com.smartpm.entity.RiskEvent;

import java.util.List;

public interface RiskService {
    default RiskOverviewVO getOverview(Long projectId) { return getOverview(projectId, null); }
    RiskOverviewVO getOverview(Long projectId, Long baselineId);
    String generateAiAnalysis(Long projectId);
    RiskAction updateAction(Long projectId, Long taskId, RiskActionUpdateDTO dto);
    List<RiskEvent> listEvents(Long projectId, Long taskId);
    void invalidate(Long projectId);
}
