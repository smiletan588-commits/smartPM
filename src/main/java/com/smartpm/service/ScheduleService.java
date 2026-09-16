package com.smartpm.service;

import com.smartpm.dto.ScheduleBaselineCreateDTO;
import com.smartpm.dto.ScheduleSimulationDTO;
import com.smartpm.vo.ScheduleAnalysisVO;
import com.smartpm.vo.ScheduleBaselineVO;
import com.smartpm.vo.ScheduleSimulationVO;

import java.util.List;

public interface ScheduleService {
    ScheduleAnalysisVO analyze(Long projectId, Long baselineId);
    ScheduleSimulationVO simulate(Long projectId, ScheduleSimulationDTO dto);
    List<ScheduleBaselineVO> listBaselines(Long projectId);
    ScheduleBaselineVO getBaseline(Long projectId, Long baselineId);
    ScheduleBaselineVO createBaseline(Long projectId, ScheduleBaselineCreateDTO dto);
    void deleteBaseline(Long projectId, Long baselineId);
}
