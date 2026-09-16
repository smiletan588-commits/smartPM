package com.smartpm.service;

import java.util.List;
import java.util.Map;

public interface ManagementOperationsService {
    Map<String, Object> executiveSummary(Long projectId);
    byte[] executiveSummaryPdf(Long projectId);
    int emailExecutiveSummary(Long projectId, List<Long> recipientUserIds);
    List<Map<String, Object>> auditEvents(Long projectId, String category, int page, int size);
    List<Map<String, Object>> loginEvents(Boolean success, int page, int size);
    Map<String, Object> systemOverview();
}
