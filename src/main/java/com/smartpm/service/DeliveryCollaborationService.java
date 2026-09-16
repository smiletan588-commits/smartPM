package com.smartpm.service;

import com.smartpm.dto.*;

import java.util.List;
import java.util.Map;

public interface DeliveryCollaborationService {
    Map<String, Object> acceptance(Long taskId);
    Map<String, Object> updateAcceptance(Long taskId, AcceptanceActionDTO dto);
    Map<String, Object> addChecklist(Long taskId, AcceptanceChecklistDTO dto);
    Map<String, Object> toggleChecklist(Long taskId, Long itemId, boolean checked);
    void deleteChecklist(Long taskId, Long itemId);
    List<Map<String, Object>> timeEntries(Long taskId);
    Map<String, Object> addTimeEntry(Long taskId, TimeEntryDTO dto);
    List<Map<String, Object>> capacity(Long projectId);
    Map<String, Object> updateCapacity(Long projectId, CapacityUpdateDTO dto);
    Map<String, Object> addCapacityException(Long projectId, CapacityExceptionDTO dto);
    void deleteCapacityException(Long projectId, Long exceptionId);
}
